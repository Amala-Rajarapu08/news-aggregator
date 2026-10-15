package com.pulseai.ingestion;

import java.time.Instant;
import java.util.List;

public record IngestionReport(
        Instant startedAt,
        Instant completedAt,
        int fetched,
        int accepted,
        int inserted,
        int imagesBackfilled,
        int duplicatesSkipped,
        int invalidSkipped,
        int failures,
        List<ProviderReport> providers,
        List<FeedIngestionReport> feedReports) {

    public IngestionReport {
        providers = List.copyOf(providers);
        feedReports = List.copyOf(feedReports);
    }

    public record ProviderReport(String provider, int fetched, int failures) {
    }
}
