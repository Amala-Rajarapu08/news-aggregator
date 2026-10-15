package com.pulseai.dto.history;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReadingHistoryRequest(
        @NotNull(message = "Article ID is required.")
        UUID articleId,
        Instant openedAt,
        @Min(value = 0, message = "Duration cannot be negative.")
        @Max(value = 86400, message = "Duration cannot exceed 24 hours.")
        int durationSeconds,
        boolean completed,
        @Min(value = 0, message = "Scroll percentage cannot be negative.")
        @Max(value = 100, message = "Scroll percentage cannot exceed 100.")
        Integer scrollPercentage) {
}
