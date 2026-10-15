package com.pulseai.ingestion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class NewsIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(NewsIngestionService.class);
    private static final int MAX_IMAGE_METADATA_LOOKUPS_PER_RUN = 24;
    private static final int MAX_IMAGE_METADATA_LOOKUPS_PER_PROVIDER_PER_RUN = 12;
    private static final int MAX_IMAGE_METADATA_LOOKUPS_PER_FEED_PER_RUN = 8;

    private final List<NewsProvider> providers;
    private final ArticleNormalizer normalizer;
    private final ArticleDeduplicationService deduplicationService;
    private final ArticlePersistenceService persistenceService;
    private final com.pulseai.service.ArticleRetentionService retention;
    private final com.pulseai.service.IngestionStatusService status;
    private final CategoryMapper categoryMapper;
    private final ArticlePageImageResolver imageResolver;

    public NewsIngestionService(
            List<NewsProvider> providers,
            ArticleNormalizer normalizer,
            ArticleDeduplicationService deduplicationService,
            ArticlePersistenceService persistenceService,
            com.pulseai.service.ArticleRetentionService retention,
            com.pulseai.service.IngestionStatusService status,
            CategoryMapper categoryMapper,
            ArticlePageImageResolver imageResolver) {
        this.providers = providers;
        this.normalizer = normalizer;
        this.deduplicationService = deduplicationService;
        this.persistenceService = persistenceService; this.retention=retention; this.status=status;
        this.categoryMapper = categoryMapper;
        this.imageResolver = imageResolver;
    }

    public synchronized IngestionReport ingest() {
        Instant startedAt = Instant.now();
        int fetched = 0;
        int accepted = 0;
        int inserted = 0;
        int imagesBackfilled = 0;
        int duplicates = 0;
        int invalid = 0;
        int failures = 0;
        int imageMetadataLookups = 0;
        Map<String, Integer> imageMetadataLookupsByProvider = new HashMap<>();
        Map<String, Integer> imageMetadataLookupsByFeed = new HashMap<>();
        List<IngestionReport.ProviderReport> providerReports = new ArrayList<>();
        Map<String, FeedStats> feedStats = new LinkedHashMap<>();

        for (NewsProvider provider : providers) {
            if (!provider.isConfigured()) {
                continue;
            }
            ProviderFetchResult batch;
            try {
                batch = provider.fetch();
            } catch (RuntimeException exception) {
                failures++;
                providerReports.add(new IngestionReport.ProviderReport(provider.name(), 0, 1));
                logger.warn(
                        "News provider failed provider={} exceptionType={}",
                        provider.name(),
                        exception.getClass().getSimpleName());
                continue;
            }
            fetched += batch.fetched();
            failures += batch.failures();
            batch.rssFeedDiagnostics().forEach(diagnostic -> feedStats.put(
                    diagnostic.feedIdentifier(), new FeedStats(diagnostic)));
            providerReports.add(new IngestionReport.ProviderReport(
                    provider.name(), batch.fetched(), batch.failures()));

            List<ProviderArticle> orderedArticles = new ArrayList<>(batch.articles());
            orderedArticles.sort(java.util.Comparator
                    .comparingInt(article -> imagePriority(article.category())));
            for (ProviderArticle providerArticle : orderedArticles) {
                FeedStats feed = providerArticle.providerIdentifier() == null
                        ? null
                        : feedStats.get(providerArticle.providerIdentifier());
                NormalizedArticle candidate;
                try {
                    candidate = normalizer.normalize(providerArticle);
                    if (candidate.publishedAt().isBefore(retention.cutoff())) {
                        invalid++;
                        if (feed != null) feed.reject("outside-retention-window");
                        continue;
                    }
                    if (candidate.publishedAt().isAfter(Instant.now().plusSeconds(300))) {
                        invalid++;
                        if (feed != null) feed.reject("future-publication-time");
                        continue;
                    }
                    accepted++;
                    if (feed != null) feed.accepted++;
                } catch (IllegalArgumentException exception) {
                    invalid++;
                    if (feed != null) feed.reject(reason(exception.getMessage()));
                    continue;
                }

                try {
                    boolean duplicate = deduplicationService.isDuplicate(candidate);
                    boolean needsImage = candidate.imageUrl() == null
                            && (!duplicate || persistenceService.needsImage(candidate.canonicalUrl()));
                    if (needsImage) {
                        boolean cachedImageResult = imageResolver.hasCachedResult(candidate);
                        String feedIdentifier = providerArticle.providerIdentifier();
                        boolean providerHasBudget = imageMetadataLookupsByProvider
                                .getOrDefault(provider.name(), 0) < MAX_IMAGE_METADATA_LOOKUPS_PER_PROVIDER_PER_RUN;
                        boolean feedHasBudget = feedIdentifier == null
                                || imageMetadataLookupsByFeed.getOrDefault(feedIdentifier, 0)
                                        < MAX_IMAGE_METADATA_LOOKUPS_PER_FEED_PER_RUN;
                        boolean lookupBudgetAvailable = imageMetadataLookups < MAX_IMAGE_METADATA_LOOKUPS_PER_RUN
                                && providerHasBudget && feedHasBudget;
                        if (cachedImageResult || lookupBudgetAvailable) {
                            if (!cachedImageResult) {
                                imageMetadataLookups++;
                                imageMetadataLookupsByProvider.merge(provider.name(), 1, Integer::sum);
                                if (feedIdentifier != null) {
                                    imageMetadataLookupsByFeed.merge(feedIdentifier, 1, Integer::sum);
                                }
                            }
                            candidate = imageResolver.resolve(candidate);
                        }
                    }
                    if (duplicate) {
                        duplicates++;
                        if (feed != null) feed.duplicates++;
                        if (persistenceService.backfillImageIfMissing(candidate)) {
                            imagesBackfilled++;
                            if (feed != null) feed.imagesBackfilled++;
                        }
                    } else if (persistenceService.persist(candidate)) {
                        inserted++;
                        if (feed != null) {
                            feed.saved++;
                            String category = categoryMapper.toSlug(candidate.category()).orElse("uncategorized");
                            feed.categoryDistribution.merge(category, 1, Integer::sum);
                        }
                        retention.cleanup();
                    } else {
                        duplicates++;
                        if (feed != null) feed.duplicates++;
                        if (persistenceService.backfillImageIfMissing(candidate)) {
                            imagesBackfilled++;
                            if (feed != null) feed.imagesBackfilled++;
                        }
                    }
                } catch (DataIntegrityViolationException exception) {
                    if (deduplicationService.isDuplicate(candidate)) {
                        duplicates++;
                        if (feed != null) feed.duplicates++;
                        if (persistenceService.backfillImageIfMissing(candidate)) {
                            imagesBackfilled++;
                            if (feed != null) feed.imagesBackfilled++;
                        }
                    } else {
                        failures++;
                        if (feed != null) feed.fail("data-integrity");
                        logger.warn("Article persistence failed provider={} reason=data-integrity", provider.name());
                    }
                } catch (RuntimeException exception) {
                    failures++;
                    if (feed != null) feed.fail("persistence-error");
                    logger.warn(
                            "Article persistence failed provider={} exceptionType={}",
                            provider.name(),
                            exception.getClass().getSimpleName());
                }
            }
        }

        Instant completedAt = Instant.now();
        logger.info(
                "News ingestion completed providers={} fetched={} accepted={} inserted={} imagesBackfilled={} duplicates={} invalid={} failures={} elapsedMs={}",
                providerReports.size(),
                fetched,
                accepted,
                inserted,
                imagesBackfilled,
                duplicates,
                invalid,
                failures,
                completedAt.toEpochMilli() - startedAt.toEpochMilli());
        retention.cleanup();
        List<FeedIngestionReport> feedReports = feedStats.values().stream().map(FeedStats::toReport).toList();
        var report = new IngestionReport(startedAt, completedAt, fetched, accepted, inserted, imagesBackfilled, duplicates,
                invalid, failures, providerReports, feedReports);
        status.record(report);
        for (FeedIngestionReport feed : feedReports) {
            logger.info("News ingestion feed summary feedIdentifier={} feedUrl={} httpStatus={} parsedItems={} acceptedItems={} duplicateItems={} rejectedItems={} rejectionReasons={} savedArticles={} imagesBackfilled={} imageCandidates={} failures={} categories={} lastSuccessfulAt={}",
                    feed.feedIdentifier(), feed.feedUrl(), feed.httpStatus(), feed.parsedItems(),
                    feed.acceptedItems(), feed.duplicateItems(), feed.rejectedItems(), feed.rejectionReasons(),
                    feed.savedArticles(), feed.imagesBackfilled(), feed.imageCandidates(), feed.failures(), feed.categoryDistribution(),
                    feed.lastSuccessfulAt());
        }
        return report;
    }

    private String reason(String message) {
        if (message == null || message.isBlank()) return "invalid-article";
        return message.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private int imagePriority(String category) {
        if (category == null) return 3;
        String value = category.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("sport")) return 0;
        if (value.contains("technolog") || value.contains("tech")) return 1;
        if (value.contains("entertainment") || value.contains("culture")) return 2;
        return 3;
    }

    private static final class FeedStats {
        private final RssFeedDiagnostic diagnostic;
        private int accepted;
        private int duplicates;
        private int rejected;
        private int saved;
        private int imagesBackfilled;
        private int failures;
        private final Map<String, Integer> rejectionReasons = new LinkedHashMap<>();
        private final Map<String, Integer> categoryDistribution = new LinkedHashMap<>();

        private FeedStats(RssFeedDiagnostic diagnostic) {
            this.diagnostic = diagnostic;
            this.failures = diagnostic.failures();
            if (diagnostic.failureReason() != null) rejectionReasons.put("feed-" + diagnostic.failureReason(), diagnostic.failures());
        }

        private void reject(String reason) {
            rejected++;
            rejectionReasons.merge(reason, 1, Integer::sum);
        }

        private void fail(String reason) {
            failures++;
            rejectionReasons.merge(reason, 1, Integer::sum);
        }

        private FeedIngestionReport toReport() {
            return new FeedIngestionReport(diagnostic.feedIdentifier(), diagnostic.feedUrl(), diagnostic.category(),
                    diagnostic.httpStatus(), diagnostic.parsedItems(), accepted, duplicates, rejected, saved,
                    imagesBackfilled,
                    diagnostic.imageCandidates(), failures, rejectionReasons, categoryDistribution,
                    diagnostic.lastSuccessfulAt());
        }
    }
}
