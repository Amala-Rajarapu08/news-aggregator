package com.pulseai.dto.event;

import java.util.List;

public record EventFeedResponse(List<EventResponse> items, int totalItems) {}
