package com.pulseai.controller;

import java.time.Instant;
import java.util.UUID;

import com.pulseai.dto.search.SemanticSearchResponse;
import com.pulseai.service.SemanticSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SemanticSearchService searchService;

    public SearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SemanticSearchResponse keyword(
            @RequestParam @NotBlank @Size(max = 500) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return searchService.keywordSearch(q, category, sourceId, from, to, page, size);
    }

    @PostMapping("/semantic")
    public SemanticSearchResponse semantic(@Valid @RequestBody Query query) {
        return searchService.search(query.query(), query.category(), query.sourceId(),
                query.from(), query.to(), query.page(), query.size());
    }

    public record Query(
            @NotBlank @Size(max = 500) String query,
            String category,
            UUID sourceId,
            Instant from,
            Instant to,
            @Min(0) @Max(1_000_000) Integer page,
            @Min(1) @Max(100) Integer size) {
        public Query {
            page = page == null ? 0 : page;
            size = size == null ? 20 : size;
        }
    }
}
