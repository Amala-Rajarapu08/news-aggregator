package com.pulseai.dto.search;

import java.util.List;

import com.pulseai.dto.article.ArticleResponse;

public record SearchArticle(ArticleResponse article, Double relevance, List<String> matchedFields) {}
