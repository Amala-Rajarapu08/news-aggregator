package com.pulseai.repository;

import java.util.UUID;

import com.pulseai.entity.EventArticle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventArticleRepository extends JpaRepository<EventArticle, UUID> {
}
