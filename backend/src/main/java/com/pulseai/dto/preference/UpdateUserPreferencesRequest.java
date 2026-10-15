package com.pulseai.dto.preference;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserPreferencesRequest(
        @NotNull(message = "Briefing frequency is required.")
        BriefingFrequencyRequest briefingFrequency,
        boolean recommendationsEnabled,
        boolean notificationsEnabled,
        @Size(max = 100, message = "At most 100 topics may be followed.")
        List<UUID> topicIds,
        boolean historyEnabled,
        @Size(max = 8) List<@jakarta.validation.constraints.NotBlank @Size(max = 100) String> categorySlugs) {

    public enum BriefingFrequencyRequest {
        DAILY,
        WEEKLY,
        OFF
    }
}
