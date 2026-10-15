package com.pulseai.dto.event;

import java.util.UUID;

public record EventPublisherResponse(UUID id, String name, int articleCount) {}
