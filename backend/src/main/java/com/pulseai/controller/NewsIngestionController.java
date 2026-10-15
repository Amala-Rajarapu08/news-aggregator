package com.pulseai.controller;

import com.pulseai.ingestion.IngestionReport;
import com.pulseai.ingestion.NewsIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ingestion")
public class NewsIngestionController {

    private final NewsIngestionService ingestionService;

    public NewsIngestionController(NewsIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/run")
    public IngestionReport run() {
        return ingestionService.ingest();
    }
}
