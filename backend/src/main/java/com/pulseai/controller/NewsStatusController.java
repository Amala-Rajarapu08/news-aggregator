package com.pulseai.controller;
import com.pulseai.service.*;
import com.pulseai.ingestion.NewsProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/news")
public class NewsStatusController {
    private final IngestionStatusService status;
    private final ArticleRetentionService retention;
    private final List<NewsProvider> providers;
    private final boolean enabled; private final long interval;
    public NewsStatusController(IngestionStatusService status, ArticleRetentionService retention, List<NewsProvider> providers,
        @Value("${app.news.ingestion.enabled:false}") boolean enabled, @Value("${app.news.ingestion.interval-ms:3600000}") long interval) {
        this.status=status;this.retention=retention;this.providers=providers;this.enabled=enabled;this.interval=interval;
    }
    @GetMapping("/status") public IngestionStatusService.Status status() {
        return status.status(enabled,interval,retention,providers.stream().filter(NewsProvider::isConfigured).map(NewsProvider::name).toList());
    }
}
