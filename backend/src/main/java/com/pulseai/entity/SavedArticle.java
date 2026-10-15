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
@Table(name = "saved_articles", uniqueConstraints =
        @UniqueConstraint(name = "uk_saved_articles_user_article", columnNames = {"user_id", "article_id"}))
public class SavedArticle {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    private Article article;

    @Column(name="article_key", nullable=false) private UUID articleKey;
    @Column(nullable=false, length=500) private String title;
    @Column(name="canonical_url", nullable=false, length=2048) private String canonicalUrl;
    @Column(nullable=false, length=120) private String publisher;
    @Column(name="publisher_domain", nullable=false, length=253) private String publisherDomain;
    @Column(name="publisher_url", nullable=false, length=2048) private String publisherUrl;
    @Column(name="publisher_id", nullable=false) private UUID publisherId;
    @Column(name="published_at", nullable=false) private Instant publishedAt;
    @Column(length=80) private String category;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SavedArticle() {
    }

    public SavedArticle(User user, Article article) {
        this.user = user;
        this.article = article;
        articleKey = article.getId(); title = article.getTitle(); canonicalUrl = article.getOriginalUrl();
        publisher = article.getSource().getName(); publisherDomain = article.getSource().getDomain();
        publisherUrl = article.getSource().getBaseUrl(); publisherId = article.getSource().getId();
        publishedAt = article.getPublishedAt(); category = article.getCategory() == null ? null : article.getCategory().getName();
    }

    public com.pulseai.dto.article.ArticleResponse snapshot() {
        return new com.pulseai.dto.article.ArticleResponse(articleKey, title, null, canonicalUrl, null, null, publishedAt, "und",
            new com.pulseai.dto.article.SourceResponse(publisherId, publisher, publisherDomain, publisherUrl), category);
    }
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Article getArticle() { return article; }
    public Instant getCreatedAt() { return createdAt; }
}
