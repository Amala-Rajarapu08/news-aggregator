package com.pulseai.repository;

import java.util.Optional;
import java.util.UUID;

import com.pulseai.entity.SavedArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedArticleRepository extends JpaRepository<SavedArticle, UUID> {
    boolean existsByUserIdAndArticleKey(UUID userId, UUID articleKey);
    long countByUserId(UUID userId);
    Optional<SavedArticle> findByUserIdAndArticleKey(UUID userId, UUID articleKey);

    @EntityGraph(attributePaths = {"article", "article.source", "article.category"})
    Page<SavedArticle> findByUserId(UUID userId, Pageable pageable);
}
