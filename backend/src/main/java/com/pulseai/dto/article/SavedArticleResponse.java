package com.pulseai.dto.article;

import java.time.Instant;
import java.util.UUID;

public record SavedArticleResponse(UUID id, Instant savedAt, ArticleResponse article, boolean archived) {
}
