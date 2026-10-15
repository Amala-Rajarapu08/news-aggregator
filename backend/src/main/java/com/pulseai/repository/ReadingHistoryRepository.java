package com.pulseai.repository;

import java.util.UUID;

import com.pulseai.entity.ReadingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, UUID> {
    void deleteByUserId(UUID userId);
    @EntityGraph(attributePaths = {"article", "article.source", "article.category"})
    Page<ReadingHistory> findByUserId(UUID userId, Pageable pageable);
}
