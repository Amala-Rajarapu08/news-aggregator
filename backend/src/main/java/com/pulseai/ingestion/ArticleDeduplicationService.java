package com.pulseai.ingestion;

import com.pulseai.repository.ArticleRepository;
import org.springframework.stereotype.Service;

@Service
public class ArticleDeduplicationService {

    private final ArticleRepository articleRepository;

    public ArticleDeduplicationService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public boolean isDuplicate(NormalizedArticle article) {
        return articleRepository.existsByCanonicalUrl(article.canonicalUrl());
    }
}
