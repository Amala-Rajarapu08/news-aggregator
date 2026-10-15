package com.pulseai.dto.common;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

public final class ApiErrorResponseFactory {

    private ApiErrorResponseFactory() {
    }

    public static ApiErrorResponse create(
            int status,
            String error,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors) {
        Object requestId = request.getAttribute("pulseai.requestId");
        String resolvedRequestId = requestId instanceof String id ? id : UUID.randomUUID().toString();
        return new ApiErrorResponse(
                Instant.now(),
                status,
                error,
                message,
                request.getRequestURI(),
                Map.copyOf(validationErrors),
                resolvedRequestId);
    }
}
