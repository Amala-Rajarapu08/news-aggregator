package com.pulseai.ingestion;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.Instant;

import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class RssNewsProvider implements NewsProvider {

    private static final Logger logger = LoggerFactory.getLogger(RssNewsProvider.class);

    private final RssFeedFetcher fetcher;
    private final RssFeedParser parser;
    private final CategoryMapper categoryMapper;
    private final List<String> feedUrls;

    public RssNewsProvider(
            RssFeedFetcher fetcher,
            RssFeedParser parser,
            CategoryMapper categoryMapper,
            @Value("${app.news.rss-feeds:}") String configuredFeeds) {
        this.fetcher = fetcher;
        this.parser = parser;
        this.categoryMapper = categoryMapper;
        this.feedUrls = Arrays.stream(configuredFeeds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    @Override
    public String name() {
        return "rss";
    }

    @Override
    public boolean isConfigured() {
        return !feedUrls.isEmpty();
    }

    @Override
    public ProviderFetchResult fetch() {
        List<ProviderArticle> articles = new ArrayList<>();
        List<RssFeedDiagnostic> diagnostics = new ArrayList<>();
        int failures = 0;
        for (String configuredFeed : feedUrls) {
            String[] parts=configuredFeed.split("\\|",2);
            String feedUrl=parts.length==2?parts[1]:parts[0];
            String category=parts.length==2?categoryMapper.toSlug(parts[0]).orElse(null):null;
            String identifier = feedIdentifier(feedUrl, category);
            int httpStatus = 0;
            try {
                RssFeedResponse response = fetcher.fetchWithStatus(feedUrl);
                httpStatus = response.statusCode();
                if (httpStatus < 200 || httpStatus >= 300) {
                    failures++;
                    diagnostics.add(new RssFeedDiagnostic(identifier, safeFeedUrl(feedUrl), category,
                            httpStatus, 0, 0, 1, "http-status", null));
                    logger.warn("RSS feed failed feedIdentifier={} httpStatus={} reason=http-status", identifier, httpStatus);
                    continue;
                }
                List<ProviderArticle> entries = parser.parse(response.body());
                int images = 0;
                for (var entry : entries) {
                    if (entry.imageUrl() != null && !entry.imageUrl().isBlank()) images++;
                    String providerCategory = categoryMapper.toSlug(entry.category()).isPresent()
                            ? entry.category()
                            : category;
                    articles.add(new ProviderArticle(
                            entry.externalId(), entry.title(), entry.description(), entry.url(), entry.imageUrl(),
                            entry.author(), entry.publishedAt(), entry.sourceName(), entry.sourceHomeUrl(),
                            providerCategory, entry.language(), identifier));
                }
                Instant successfulAt = Instant.now();
                diagnostics.add(new RssFeedDiagnostic(identifier, safeFeedUrl(feedUrl), category,
                        httpStatus, entries.size(), images, 0, null, successfulAt));
                logger.info("RSS feed fetched feedIdentifier={} httpStatus={} parsedItems={} imageCandidates={} category={}",
                        identifier, httpStatus, entries.size(), images, category == null ? "provider-metadata" : category);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failures++;
                diagnostics.add(new RssFeedDiagnostic(identifier, safeFeedUrl(feedUrl), category,
                        httpStatus, 0, 0, 1, "interrupted", null));
                logger.warn("RSS feed failed feedIdentifier={} httpStatus={} reason=interrupted", identifier, httpStatus);
                break;
            } catch (java.io.IOException | SAXException | ParserConfigurationException exception) {
                failures++;
                diagnostics.add(new RssFeedDiagnostic(identifier, safeFeedUrl(feedUrl), category,
                        httpStatus, 0, 0, 1, exception.getClass().getSimpleName(), null));
                logger.warn("RSS feed failed feedIdentifier={} httpStatus={} reason={}",
                        identifier, httpStatus, exception.getClass().getSimpleName());
            }
        }
        return new ProviderFetchResult(articles, articles.size(), failures, diagnostics);
    }

    private String feedIdentifier(String feedUrl, String category) {
        try {
            URI uri = URI.create(feedUrl);
            if (uri.getHost() == null || uri.getUserInfo() != null) return "invalid-feed-url";
            return (category == null ? "unmapped" : category) + "@" + uri.getHost().toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return "invalid-feed-url";
        }
    }

    private String safeFeedUrl(String feedUrl) {
        try {
            URI uri = URI.create(feedUrl);
            if (uri.getHost() == null || uri.getUserInfo() != null) return "invalid-feed-url";
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), "/", null, null).toASCIIString();
        } catch (java.net.URISyntaxException | IllegalArgumentException exception) {
            return "invalid-feed-url";
        }
    }
}
