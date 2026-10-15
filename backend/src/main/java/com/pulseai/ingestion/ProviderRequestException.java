package com.pulseai.ingestion;

import java.io.IOException;

/** Safe provider failure details for logs; it deliberately omits request URLs and credentials. */
public final class ProviderRequestException extends IOException {

    private final int statusCode;
    private final String reason;

    private ProviderRequestException(int statusCode, String reason, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.reason = reason;
    }

    public static ProviderRequestException httpStatus(int statusCode) {
        return new ProviderRequestException(
                statusCode, "http-status", "Provider returned HTTP " + statusCode + ".", null);
    }

    public static ProviderRequestException httpStatus(int statusCode, String reason) {
        return new ProviderRequestException(
                statusCode, reason, "Provider returned HTTP " + statusCode + ".", null);
    }

    public static ProviderRequestException cooldown(int statusCode) {
        return new ProviderRequestException(
                statusCode, "rate-limit-cooldown", "Provider is in a rate-limit cooldown.", null);
    }

    public static ProviderRequestException failure(String reason, String message, Throwable cause) {
        return new ProviderRequestException(0, reason, message, cause);
    }

    public int statusCode() {
        return statusCode;
    }

    public String reason() {
        return reason;
    }
}
