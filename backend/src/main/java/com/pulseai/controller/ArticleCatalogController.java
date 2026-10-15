package com.pulseai.controller;

import java.time.Instant;
import java.util.UUID;

import com.pulseai.dto.article.ArticleResponse;
import com.pulseai.dto.common.PageResponse;
import com.pulseai.service.ArticleCatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/articles")
public class ArticleCatalogController {

    private final ArticleCatalogService catalogService;

    public ArticleCatalogController(ArticleCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public PageResponse<ArticleResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalogService.articles(q, category, sourceId, from, to, page, size);
    }

    @GetMapping("/{id}")
    public ArticleResponse get(@PathVariable UUID id) {
        return catalogService.article(id);
    }
}
