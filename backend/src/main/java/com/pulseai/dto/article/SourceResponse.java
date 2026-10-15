package com.pulseai.dto.article;

import java.util.UUID;

public record SourceResponse(UUID id, String name, String domain, String baseUrl) {
}
