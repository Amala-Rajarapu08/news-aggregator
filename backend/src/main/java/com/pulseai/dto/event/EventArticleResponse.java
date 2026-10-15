package com.pulseai.dto.event;

import java.time.Instant;
import java.util.UUID;

public record EventArticleResponse(
        UUID id,
        String title,
        String description,
        String publisher,
        Instant publishedAt,
        String url) {}
