package com.pulseai.dto.search;

import java.util.List;

public record SemanticSearchResponse(
        String mode,
        String model,
        String reason,
        long indexedArticles,
        long totalItems,
        int page,
        int pageSize,
        int totalPages,
        List<SearchArticle> items) {}
