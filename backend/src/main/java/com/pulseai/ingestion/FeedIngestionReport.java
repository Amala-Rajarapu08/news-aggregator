package com.pulseai.ingestion;

import java.time.Instant;
import java.util.Map;

public record FeedIngestionReport(
        String feedIdentifier,
        String feedUrl,
        String category,
        int httpStatus,
        int parsedItems,
        int acceptedItems,
        int duplicateItems,
        int rejectedItems,
        int savedArticles,
        int imagesBackfilled,
        int imageCandidates,
        int failures,
        Map<String, Integer> rejectionReasons,
        Map<String, Integer> categoryDistribution,
        Instant lastSuccessfulAt) {

    public FeedIngestionReport {
        rejectionReasons = Map.copyOf(rejectionReasons);
        categoryDistribution = Map.copyOf(categoryDistribution);
    }
}
