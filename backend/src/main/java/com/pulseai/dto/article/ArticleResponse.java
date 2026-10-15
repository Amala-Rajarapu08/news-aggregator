package com.pulseai.dto.article;

import java.time.Instant;
import java.util.UUID;

public record ArticleResponse(
        UUID id,
        String title,
        String description,
        String url,
        String imageUrl,
        String author,
        Instant publishedAt,
        String language,
        SourceResponse source,
        String category) {
}
