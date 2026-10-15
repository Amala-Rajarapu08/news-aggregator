package com.pulseai.entity;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
@Entity @Table(name = "browser_sessions")
public class BrowserSession {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected BrowserSession() {}
    public BrowserSession(UUID userId, String hash, Instant expiry) {
        id = UUID.randomUUID(); this.userId = userId; tokenHash = hash; expiresAt = expiry; createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void rotate(String hash) { tokenHash = hash; }
}
