package com.pulseai.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sources")
public class Source {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 253)
    private String domain;

    @Column(name = "base_url", nullable = false, length = 2048)
    private String baseUrl;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Source() {
    }

    public Source(String name, String domain, String baseUrl) {
        this.name = name;
        this.domain = domain;
        this.baseUrl = baseUrl;
        this.active = true;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDomain() { return domain; }
    public String getBaseUrl() { return baseUrl; }
    public String getLogoUrl() { return logoUrl; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
