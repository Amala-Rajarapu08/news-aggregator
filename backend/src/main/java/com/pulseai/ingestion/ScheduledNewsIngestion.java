package com.pulseai.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledNewsIngestion {

    private final NewsIngestionService ingestionService;
    private final boolean enabled;

    public ScheduledNewsIngestion(
            NewsIngestionService ingestionService,
            @Value("${app.news.ingestion.enabled:false}") boolean enabled) {
        this.ingestionService = ingestionService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${app.news.ingestion.interval-ms:3600000}",
            initialDelayString = "${app.news.ingestion.initial-delay-ms:5000}")
    public void runWhenEnabled() {
        if (enabled) {
            ingestionService.ingest();
        }
    }
}
