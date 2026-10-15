package com.pulseai.dto.history;

import java.time.Instant;
import java.util.UUID;

import com.pulseai.dto.article.ArticleResponse;

public record ReadingHistoryResponse(
        UUID id,
        Instant openedAt,
        int durationSeconds,
        boolean completed,
        Integer scrollPercentage,
        ArticleResponse article) {
}
