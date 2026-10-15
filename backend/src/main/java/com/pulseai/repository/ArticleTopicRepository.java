package com.pulseai.repository;

import java.util.UUID;

import com.pulseai.entity.ArticleTopic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleTopicRepository extends JpaRepository<ArticleTopic, UUID> {
}
