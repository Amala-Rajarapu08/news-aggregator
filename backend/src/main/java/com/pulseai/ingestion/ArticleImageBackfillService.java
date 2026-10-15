package com.pulseai.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.pulseai.entity.Article;
import com.pulseai.entity.Source;
import com.pulseai.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Audits stored images and fills missing images in bounded, category-prioritized batches. */
@Service
@ConditionalOnProperty(
        prefix = "app.news",
        name = {"image-metadata-enabled", "image-backfill.enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class ArticleImageBackfillService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleImageBackfillService.class);
    private static final int MAX_LOOKUPS_PER_BATCH = 12;
    private static final int MAX_VALIDATIONS_PER_BATCH = 32;
    private static final int MAX_SCAN_SIZE = 128;
    private static final Duration CHECK_TTL = Duration.ofHours(6);

    private final ArticleRepository articles;
    private final ArticlePageImageResolver imageResolver;
    private final ArticleImageValidator imageValidator;
    private final ArticlePersistenceService persistence;
    private final int lookupBatchSize;
    private final int validationBatchSize;
    private final int scanSize;
    private int scanPage;
    private int scanIndex;

    public ArticleImageBackfillService(
            ArticleRepository articles,
            ArticlePageImageResolver imageResolver,
            ArticleImageValidator imageValidator,
            ArticlePersistenceService persistence,
            @Value("${app.news.image-backfill.batch-size:8}") int lookupBatchSize,
            @Value("${app.news.image-backfill.validation-batch-size:24}") int validationBatchSize,
            @Value("${app.news.image-backfill.scan-size:64}") int scanSize) {
        this.articles = articles;
        this.imageResolver = imageResolver;
        this.imageValidator = imageValidator;
        this.persistence = persistence;
        this.lookupBatchSize = Math.max(1, Math.min(MAX_LOOKUPS_PER_BATCH, lookupBatchSize));
        this.validationBatchSize = Math.max(1, Math.min(MAX_VALIDATIONS_PER_BATCH, validationBatchSize));
        this.scanSize = Math.max(1, Math.min(MAX_SCAN_SIZE, scanSize));
    }

    @Scheduled(
            fixedDelayString = "${app.news.image-backfill.interval-ms:300000}",
            initialDelayString = "${app.news.image-backfill.initial-delay-ms:60000}")
    public void runScheduledBatch() {
        BatchResult result = backfillBatch();
        if (result.enriched() > 0 || result.invalidated() > 0) {
            logger.info("Article image audit completed scanned={} validated={} invalid={} lookups={} enriched={} cleared={}",
                    result.scanned(), result.validations(), result.invalidUrls(), result.lookups(),
                    result.enriched(), result.invalidated());
        } else if (result.scanned() > 0) {
            logger.debug("Article image audit completed scanned={} validated={} invalid={} lookups={} enriched=0 cleared=0",
                    result.scanned(), result.validations(), result.invalidUrls(), result.lookups());
        }
    }

    public synchronized BatchResult backfillBatch() {
        Slice<Article> batch = articles.findImageAuditCandidates(PageRequest.of(scanPage, scanSize));
        if (batch.isEmpty()) {
            scanPage = 0;
            scanIndex = 0;
            return new BatchResult(0, 0, 0, 0, 0, 0);
        }

        List<Article> content = batch.getContent();
        int scanned = 0;
        int lookups = 0;
        int validations = 0;
        int invalidUrls = 0;
        int enriched = 0;
        int invalidated = 0;
        Instant checkedBefore = Instant.now().minus(CHECK_TTL);

        while (scanIndex < content.size()) {
            Article article = content.get(scanIndex);
            if (article.getImageCheckedAt() != null && article.getImageCheckedAt().isAfter(checkedBefore)) {
                scanIndex++;
                scanned++;
                continue;
            }
            String currentImageUrl = article.getImageUrl();
            boolean hasImageUrl = currentImageUrl != null && !currentImageUrl.isBlank();

            if (hasImageUrl && !imageValidator.hasCachedResult(currentImageUrl)
                    && validations >= validationBatchSize) {
                break;
            }

            NormalizedArticle candidate = candidate(article);
            if (hasImageUrl) {
                ArticleImageValidator.ValidationResult validation;
                if (imageValidator.hasCachedResult(currentImageUrl)) {
                    validation = imageValidator.validate(currentImageUrl);
                } else {
                    validations++;
                    validation = imageValidator.validate(currentImageUrl);
                }
                if (validation == ArticleImageValidator.ValidationResult.VALID
                        || validation == ArticleImageValidator.ValidationResult.UNAVAILABLE) {
                    if (validation == ArticleImageValidator.ValidationResult.VALID) {
                        persistence.markImageCheckedByIdIfMatches(article.getId(), currentImageUrl, Instant.now());
                    }
                    scanIndex++;
                    scanned++;
                    continue;
                }
                invalidUrls++;
            }

            boolean metadataCached = candidate == null || imageResolver.hasCachedResult(candidate);
            if (candidate != null && !metadataCached && lookups >= lookupBatchSize) {
                break;
            }

            NormalizedArticle resolved = candidate == null ? null : imageResolver.resolve(candidate);
            if (candidate != null && !metadataCached) lookups++;
            String replacement = resolved == null ? null : resolved.imageUrl();
            Instant checkedAt = Instant.now();

            if (hasImageUrl) {
                if (persistence.replaceImageByIdIfMatches(article.getId(), currentImageUrl, replacement, checkedAt)) {
                    if (replacement == null) invalidated++;
                    else enriched++;
                }
            } else if (replacement != null) {
                if (persistence.backfillImageByIdIfMissing(article.getId(), replacement, checkedAt)) enriched++;
            } else {
                persistence.markImageCheckedByIdIfMatches(article.getId(), currentImageUrl, checkedAt);
            }

            scanIndex++;
            scanned++;
        }

        if (scanIndex >= content.size()) {
            scanIndex = 0;
            scanPage = batch.hasNext() ? scanPage + 1 : 0;
        }
        return new BatchResult(scanned, lookups, validations, invalidUrls, enriched, invalidated);
    }

    private NormalizedArticle candidate(Article article) {
        Source source = article.getSource();
        if (source == null || article.getOriginalUrl() == null || article.getCanonicalUrl() == null) {
            return null;
        }
        return new NormalizedArticle(
                null,
                article.getTitle(),
                article.getDescription(),
                article.getOriginalUrl(),
                article.getCanonicalUrl(),
                null,
                article.getAuthor(),
                article.getPublishedAt(),
                source.getName(),
                source.getDomain(),
                source.getBaseUrl(),
                article.getCategory() == null ? null : article.getCategory().getName(),
                article.getLanguage());
    }

    public record BatchResult(
            int scanned,
            int lookups,
            int validations,
            int invalidUrls,
            int enriched,
            int invalidated) {
    }
}
