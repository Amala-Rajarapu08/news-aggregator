package com.pulseai.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String title,
        String description,
        String category,
        String status,
        Instant firstReportedAt,
        Instant latestReportedAt,
        int articleCount,
        int sourceCount,
        List<EventPublisherResponse> sources) {}
