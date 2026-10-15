package com.pulseai.ingestion;

public record ProviderArticle(
        String externalId,
        String title,
        String description,
        String url,
        String imageUrl,
        String author,
        String publishedAt,
        String sourceName,
        String sourceHomeUrl,
        String category,
        String language,
        String providerIdentifier) {

    public ProviderArticle(
            String externalId,
            String title,
            String description,
            String url,
            String imageUrl,
            String author,
            String publishedAt,
            String sourceName,
            String sourceHomeUrl,
            String category,
            String language) {
        this(externalId, title, description, url, imageUrl, author, publishedAt,
                sourceName, sourceHomeUrl, category, language, null);
    }
}
