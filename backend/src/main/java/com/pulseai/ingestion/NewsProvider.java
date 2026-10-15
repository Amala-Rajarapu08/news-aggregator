package com.pulseai.ingestion;

public interface NewsProvider {
    String name();

    boolean isConfigured();

    ProviderFetchResult fetch();
}
