package com.pulseai.controller;

import java.util.List;

import com.pulseai.dto.article.CategoryResponse;
import com.pulseai.service.ArticleCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryCatalogController {

    private final ArticleCatalogService catalogService;

    public CategoryCatalogController(ArticleCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return catalogService.categories();
    }
}
