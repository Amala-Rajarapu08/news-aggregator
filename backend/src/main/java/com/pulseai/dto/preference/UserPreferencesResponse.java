package com.pulseai.dto.preference;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserPreferencesResponse(
        String briefingFrequency,
        boolean recommendationsEnabled,
        boolean notificationsEnabled,
        List<UUID> topicIds,
        Instant updatedAt,
        boolean historyEnabled,
        List<String> categorySlugs) {
}
