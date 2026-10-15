package com.pulseai.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "briefing_frequency", nullable = false, length = 16)
    private BriefingFrequency briefingFrequency = BriefingFrequency.DAILY;

    @Column(name = "recommendations_enabled", nullable = false)
    private boolean recommendationsEnabled = true;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @Column(name="history_enabled", nullable=false) private boolean historyEnabled;
    @jakarta.persistence.ElementCollection
    @jakarta.persistence.CollectionTable(name="user_preferred_categories", joinColumns=@JoinColumn(name="preference_id"))
    @Column(name="category_slug", length=100, nullable=false)
    private java.util.Set<String> categorySlugs = new java.util.HashSet<>();
    public boolean isHistoryEnabled() { return historyEnabled; }
    public java.util.Set<String> getCategorySlugs() { return categorySlugs; }
    public void updateDataControls(boolean history, java.util.List<String> categories) {
        historyEnabled = history; categorySlugs.clear(); categorySlugs.addAll(categories);
    }
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreference() {
    }

    public UserPreference(User user) {
        this.user = user;
    }

    public void update(BriefingFrequency frequency, boolean recommendationsEnabled, boolean notificationsEnabled) {
        this.briefingFrequency = frequency;
        this.recommendationsEnabled = recommendationsEnabled;
        this.notificationsEnabled = notificationsEnabled;
    }

    public UUID getId() { return id; }
    public BriefingFrequency getBriefingFrequency() { return briefingFrequency; }
    public boolean isRecommendationsEnabled() { return recommendationsEnabled; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public Instant getUpdatedAt() { return updatedAt; }
}
