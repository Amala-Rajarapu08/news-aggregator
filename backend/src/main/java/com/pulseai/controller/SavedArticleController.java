package com.pulseai.controller;

import java.net.URI;
import java.util.UUID;

import com.pulseai.dto.article.SavedArticleResponse;
import com.pulseai.dto.common.PageResponse;
import com.pulseai.security.CurrentUser;
import com.pulseai.service.SavedArticleService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users/me/saved")
public class SavedArticleController {

    private final SavedArticleService savedArticleService;

    public SavedArticleController(SavedArticleService savedArticleService) {
        this.savedArticleService = savedArticleService;
    }

    @GetMapping
    public PageResponse<SavedArticleResponse> list(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return savedArticleService.list(userId, page, size);
    }

    @PostMapping("/{articleId}")
    public ResponseEntity<SavedArticleResponse> save(
            @CurrentUser UUID userId,
            @PathVariable UUID articleId) {
        SavedArticleResponse response = savedArticleService.save(userId, articleId);
        return ResponseEntity.created(URI.create("/api/v1/users/me/saved/" + articleId)).body(response);
    }

    @DeleteMapping("/{articleId}")
    public ResponseEntity<Void> remove(@CurrentUser UUID userId, @PathVariable UUID articleId) {
        savedArticleService.remove(userId, articleId);
        return ResponseEntity.noContent().build();
    }
}
