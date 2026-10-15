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

@Entity
@Table(name = "reading_history")
public class ReadingHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "scroll_percentage")
    private Integer scrollPercentage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReadingHistory() {
    }

    public ReadingHistory(
            User user,
            Article article,
            Instant openedAt,
            int durationSeconds,
            boolean completed,
            Integer scrollPercentage) {
        this.user = user;
        this.article = article;
        this.openedAt = openedAt;
        this.durationSeconds = durationSeconds;
        this.completed = completed;
        this.scrollPercentage = scrollPercentage;
    }

    public UUID getId() { return id; }
    public Article getArticle() { return article; }
    public Instant getOpenedAt() { return openedAt; }
    public int getDurationSeconds() { return durationSeconds; }
    public boolean isCompleted() { return completed; }
    public Integer getScrollPercentage() { return scrollPercentage; }
}
