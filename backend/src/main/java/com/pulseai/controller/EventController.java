package com.pulseai.controller;

import java.util.UUID;

import com.pulseai.dto.event.EventDetailResponse;
import com.pulseai.dto.event.EventFeedResponse;
import com.pulseai.service.EventIntelligenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventIntelligenceService eventService;

    public EventController(EventIntelligenceService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public EventFeedResponse list() {
        return eventService.events();
    }

    @GetMapping("/{id}")
    public EventDetailResponse detail(@PathVariable UUID id) {
        return eventService.event(id);
    }
}
