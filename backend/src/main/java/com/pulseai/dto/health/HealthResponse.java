package com.pulseai.dto.health;

import java.time.Instant;

public record HealthResponse(
        String status,
        String application,
        String environment,
        Instant timestamp,
        String requestId) {
}
