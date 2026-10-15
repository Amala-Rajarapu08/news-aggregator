package com.pulseai.ingestion;

import java.time.Instant;

public record RssFeedDiagnostic(
        String feedIdentifier,
        String feedUrl,
        String category,
        int httpStatus,
        int parsedItems,
        int imageCandidates,
        int failures,
        String failureReason,
        Instant lastSuccessfulAt) {
}
