package com.pulseai.dto.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String role, Instant createdAt) {
}
