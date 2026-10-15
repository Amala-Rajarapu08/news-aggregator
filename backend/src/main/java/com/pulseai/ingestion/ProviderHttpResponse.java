package com.pulseai.ingestion;

import java.net.URI;

public record ProviderHttpResponse(int statusCode, byte[] body, URI redirectLocation, String contentType) {
    public ProviderHttpResponse(int statusCode, byte[] body) {
        this(statusCode, body, null, null);
    }

    public ProviderHttpResponse(int statusCode, byte[] body, URI redirectLocation) {
        this(statusCode, body, redirectLocation, null);
    }
}
