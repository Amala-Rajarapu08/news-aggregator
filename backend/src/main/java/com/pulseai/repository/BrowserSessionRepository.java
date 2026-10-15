package com.pulseai.repository;
import com.pulseai.entity.BrowserSession;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface BrowserSessionRepository extends JpaRepository<BrowserSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<BrowserSession> findByTokenHash(String hash);
    boolean existsByIdAndUserIdAndExpiresAtAfter(UUID id, UUID userId, Instant now);
    long countByUserId(UUID userId);
    void deleteByExpiresAtBefore(Instant now);
}
