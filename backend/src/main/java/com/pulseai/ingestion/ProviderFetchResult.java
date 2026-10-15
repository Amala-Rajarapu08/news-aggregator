package com.pulseai.ingestion;

import java.util.List;

public record ProviderFetchResult(
        List<ProviderArticle> articles,
        int fetched,
        int failures,
        List<RssFeedDiagnostic> rssFeedDiagnostics) {

    public ProviderFetchResult {
        articles = List.copyOf(articles);
        rssFeedDiagnostics = List.copyOf(rssFeedDiagnostics);
    }

    public ProviderFetchResult(List<ProviderArticle> articles, int fetched, int failures) {
        this(articles, fetched, failures, List.of());
    }
}
