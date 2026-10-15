package com.pulseai.ingestion;

import java.time.Instant;

public record NormalizedArticle(
        String externalId,
        String title,
        String description,
        String url,
        String canonicalUrl,
        String imageUrl,
        String author,
        Instant publishedAt,
        String sourceName,
        String sourceDomain,
        String sourceHomeUrl,
        String category,
        String language) {

    public NormalizedArticle withImageUrl(String value) {
        return new NormalizedArticle(externalId, title, description, url, canonicalUrl, value, author,
                publishedAt, sourceName, sourceDomain, sourceHomeUrl, category, language);
    }
}
