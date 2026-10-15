package com.pulseai.dto.article;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String slug) {
}
