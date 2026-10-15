package com.pulseai.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "article_topics", uniqueConstraints =
        @UniqueConstraint(name = "uk_article_topics_article_topic", columnNames = {"article_id", "topic_id"}))
public class ArticleTopic {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "extraction_source", nullable = false, length = 32)
    private SignalSource extractionSource;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ArticleTopic() {
    }

    public UUID getId() { return id; }
    public Article getArticle() { return article; }
    public Topic getTopic() { return topic; }
    public Double getRelevanceScore() { return relevanceScore; }
    public SignalSource getExtractionSource() { return extractionSource; }
    public Instant getCreatedAt() { return createdAt; }
}
