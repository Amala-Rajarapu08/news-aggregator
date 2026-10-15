package com.pulseai.ingestion;

import java.net.URI;
import java.util.Locale;

import com.pulseai.entity.Article;
import com.pulseai.entity.Category;
import com.pulseai.entity.Source;
import com.pulseai.repository.ArticleRepository;
import com.pulseai.repository.CategoryRepository;
import com.pulseai.repository.SourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticlePersistenceService {

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public ArticlePersistenceService(
            ArticleRepository articleRepository,
            SourceRepository sourceRepository,
            CategoryRepository categoryRepository,
            CategoryMapper categoryMapper) {
        this.articleRepository = articleRepository;
        this.sourceRepository = sourceRepository;
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    public boolean persist(NormalizedArticle candidate) {
        if (articleRepository.existsByCanonicalUrl(candidate.canonicalUrl())) {
            return false;
        }
        URI sourceUri = URI.create(candidate.sourceHomeUrl());
        String domain = sourceUri.getHost().toLowerCase(Locale.ROOT);
        Source source = sourceRepository.findByDomainIgnoreCase(domain)
                .orElseGet(() -> sourceRepository.saveAndFlush(
                        new Source(candidate.sourceName(), domain, candidate.sourceHomeUrl())));
        Category category = categoryMapper.toSlug(candidate.category())
                .flatMap(categoryRepository::findBySlugIgnoreCase)
                .orElse(null);
        articleRepository.saveAndFlush(new Article(
                candidate.title(),
                candidate.description(),
                candidate.canonicalUrl(),
                candidate.url(),
                candidate.imageUrl(),
                candidate.author(),
                candidate.publishedAt(),
                candidate.language(),
                source,
                category));
        return true;
    }

    @Transactional
    public boolean backfillImageIfMissing(NormalizedArticle candidate) {
        if (candidate.imageUrl() == null || candidate.imageUrl().isBlank()) {
            return false;
        }
        Article existing = articleRepository.findByCanonicalUrl(candidate.canonicalUrl()).orElse(null);
        if (existing == null || (existing.getImageUrl() != null && !existing.getImageUrl().isBlank())) {
            return false;
        }
        existing.setImageUrl(candidate.imageUrl());
        return true;
    }

    @Transactional
    public boolean backfillImageByIdIfMissing(java.util.UUID articleId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        Article existing = articleRepository.findById(articleId).orElse(null);
        if (existing == null || (existing.getImageUrl() != null && !existing.getImageUrl().isBlank())) {
            return false;
        }
        existing.setImageUrl(imageUrl);
        return true;
    }

    @Transactional
    public boolean backfillImageByIdIfMissing(java.util.UUID articleId, String imageUrl, java.time.Instant checkedAt) {
        if (imageUrl == null || imageUrl.isBlank()) return false;
        Article existing = articleRepository.findById(articleId).orElse(null);
        if (existing == null || (existing.getImageUrl() != null && !existing.getImageUrl().isBlank())) return false;
        existing.setImageUrl(imageUrl);
        existing.setImageCheckedAt(checkedAt);
        return true;
    }

    @Transactional
    public boolean replaceImageByIdIfMatches(java.util.UUID articleId, String previousImageUrl, String replacementImageUrl) {
        Article existing = articleRepository.findById(articleId).orElse(null);
        if (existing == null || !java.util.Objects.equals(existing.getImageUrl(), previousImageUrl)) {
            return false;
        }
        existing.setImageUrl(replacementImageUrl);
        return true;
    }

    @Transactional
    public boolean replaceImageByIdIfMatches(
            java.util.UUID articleId,
            String previousImageUrl,
            String replacementImageUrl,
            java.time.Instant checkedAt) {
        Article existing = articleRepository.findById(articleId).orElse(null);
        if (existing == null || !java.util.Objects.equals(existing.getImageUrl(), previousImageUrl)) return false;
        existing.setImageUrl(replacementImageUrl);
        existing.setImageCheckedAt(checkedAt);
        return true;
    }

    @Transactional
    public boolean markImageCheckedByIdIfMatches(
            java.util.UUID articleId,
            String observedImageUrl,
            java.time.Instant checkedAt) {
        Article existing = articleRepository.findById(articleId).orElse(null);
        if (existing == null || !java.util.Objects.equals(existing.getImageUrl(), observedImageUrl)) return false;
        existing.setImageCheckedAt(checkedAt);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean needsImage(String canonicalUrl) {
        return articleRepository.findByCanonicalUrl(canonicalUrl)
                .map(article -> article.getImageUrl() == null || article.getImageUrl().isBlank())
                .orElse(false);
    }
}
