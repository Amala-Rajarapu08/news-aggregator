package com.pulseai.service;

import java.time.Instant;
import java.util.UUID;

import com.pulseai.dto.article.ArticleResponse;
import com.pulseai.dto.article.CategoryResponse;
import com.pulseai.dto.article.SourceResponse;
import com.pulseai.dto.common.PageResponse;
import com.pulseai.exception.ResourceNotFoundException;
import com.pulseai.repository.ArticleRepository;
import com.pulseai.repository.CategoryRepository;
import com.pulseai.repository.SourceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleCatalogService {

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;
    private final CategoryRepository categoryRepository;
    private final UserMapper userMapper;

    public ArticleCatalogService(
            ArticleRepository articleRepository,
            SourceRepository sourceRepository,
            CategoryRepository categoryRepository,
            UserMapper userMapper) {
        this.articleRepository = articleRepository;
        this.sourceRepository = sourceRepository;
        this.categoryRepository = categoryRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleResponse> articles(
            String query,
            String category,
            UUID sourceId,
            Instant from,
            Instant to,
            int page,
            int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The from date must not be after the to date.");
        }
        var result = articleRepository.findAll((root, criteria, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            String term = blankToNull(query);
            if (term != null) {
                String pattern = "%" + term.toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var source = root.join("source", jakarta.persistence.criteria.JoinType.INNER);
                var articleCategory = root.join("category", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(source.get("name")), pattern, '\\'),
                        cb.like(cb.lower(source.get("domain")), pattern, '\\'),
                        cb.like(cb.lower(articleCategory.get("name")), pattern, '\\'),
                        cb.like(cb.lower(articleCategory.get("slug")), pattern, '\\')));
            }
            String section = blankToNull(category);
            if (section != null) {
                var joined = root.join("category", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.or(cb.equal(cb.lower(joined.get("slug")), section.toLowerCase(java.util.Locale.ROOT)), cb.equal(cb.lower(joined.get("name")), section.toLowerCase(java.util.Locale.ROOT))));
            }
            if (sourceId != null) predicates.add(cb.equal(root.get("source").get("id"), sourceId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("publishedAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("publishedAt"), to));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by("id"))));
        return PageResponse.from(result, result.getContent().stream().map(userMapper::toArticleResponse).toList());
    }

    @Transactional(readOnly = true)
    public ArticleResponse article(UUID id) {
        return articleRepository.findById(id)
                .map(userMapper::toArticleResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Article was not found."));
    }

    @Transactional(readOnly = true)
    public java.util.List<ArticleResponse> articlesByIds(java.util.List<UUID> ids) {
        var byId = articleRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(com.pulseai.entity.Article::getId, article -> article));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .map(userMapper::toArticleResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SourceResponse> sources(int page, int size) {
        var result = sourceRepository.findAllByActiveTrueOrderByNameAsc(PageRequest.of(page, size));
        return PageResponse.from(result, result.getContent().stream()
                .map(source -> new SourceResponse(source.getId(), source.getName(), source.getDomain(), source.getBaseUrl()))
                .toList());
    }

    @Transactional(readOnly = true)
    public java.util.List<SourceResponse> availableSources(String category, String query) {
        String section = blankToNull(category);
        String normalizedCategory = section == null ? "" : section.toLowerCase(java.util.Locale.ROOT);
        String normalizedQuery = blankToNull(query);
        normalizedQuery = normalizedQuery == null ? "" : normalizedQuery.toLowerCase(java.util.Locale.ROOT);
        return sourceRepository.findAvailableForCategory(normalizedCategory, normalizedQuery).stream()
                .map(source -> new SourceResponse(source.getId(), source.getName(), source.getDomain(), source.getBaseUrl()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SourceResponse source(UUID id) {
        var source = sourceRepository.findById(id)
                .filter(com.pulseai.entity.Source::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Source was not found."));
        return new SourceResponse(source.getId(), source.getName(), source.getDomain(), source.getBaseUrl());
    }

    @Transactional(readOnly = true)
    public java.util.List<CategoryResponse> categories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName(), category.getSlug()))
                .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
