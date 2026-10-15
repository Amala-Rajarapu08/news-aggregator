package com.pulseai.controller;

import java.util.UUID;
import java.util.List;

import com.pulseai.dto.article.SourceResponse;
import com.pulseai.dto.common.PageResponse;
import com.pulseai.service.ArticleCatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/sources")
public class SourceCatalogController {

    private final ArticleCatalogService catalogService;

    public SourceCatalogController(ArticleCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public PageResponse<SourceResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return catalogService.sources(page, size);
    }

    @GetMapping("/available")
    public List<SourceResponse> available(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        return catalogService.availableSources(category, q);
    }

    @GetMapping("/{id}")
    public SourceResponse get(@PathVariable UUID id) {
        return catalogService.source(id);
    }
}
