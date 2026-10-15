package com.pulseai.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "articles")
public class Article {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "canonical_url", nullable = false, unique = true, length = 2048)
    private String canonicalUrl;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "image_checked_at")
    private Instant imageCheckedAt;

    @Column(length = 200)
    private String author;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(nullable = false, length = 12)
    private String language;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Article() {
    }

    public Article(
            String title,
            String description,
            String canonicalUrl,
            String imageUrl,
            String author,
            Instant publishedAt,
            String language,
            Source source,
            Category category) {
        this(title, description, canonicalUrl, canonicalUrl, imageUrl, author,
                publishedAt, language, source, category);
    }

    public Article(
            String title,
            String description,
            String canonicalUrl,
            String originalUrl,
            String imageUrl,
            String author,
            Instant publishedAt,
            String language,
            Source source,
            Category category) {
        this.title = title;
        this.description = description;
        this.canonicalUrl = canonicalUrl;
        this.originalUrl = originalUrl;
        this.imageUrl = imageUrl;
        this.author = author;
        this.publishedAt = publishedAt;
        this.language = language;
        this.source = source;
        this.category = category;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public String getOriginalUrl() { return originalUrl; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getImageCheckedAt() { return imageCheckedAt; }
    public void setImageCheckedAt(Instant imageCheckedAt) { this.imageCheckedAt = imageCheckedAt; }
    public String getAuthor() { return author; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLanguage() { return language; }
    public Source getSource() { return source; }
    public Category getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
}
