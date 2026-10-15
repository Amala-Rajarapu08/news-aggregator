package com.pulseai.dto.auth;

import com.pulseai.dto.user.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user) {
    @Override public String toString() { return "AuthResponse[token=REDACTED]"; }
}
