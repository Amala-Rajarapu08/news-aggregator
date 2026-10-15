package com.pulseai.controller;

import java.net.URI;
import java.util.UUID;

import com.pulseai.dto.common.PageResponse;
import com.pulseai.dto.history.ReadingHistoryRequest;
import com.pulseai.dto.history.ReadingHistoryResponse;
import com.pulseai.security.CurrentUser;
import com.pulseai.service.ReadingHistoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users/me/history")
public class ReadingHistoryController {

    private final ReadingHistoryService historyService;
    private final com.pulseai.repository.ReadingHistoryRepository history;

    public ReadingHistoryController(ReadingHistoryService historyService, com.pulseai.repository.ReadingHistoryRepository history) {
        this.historyService = historyService; this.history = history;
    }

    @PostMapping
    public ResponseEntity<ReadingHistoryResponse> record(
            @CurrentUser UUID userId,
            @Valid @RequestBody ReadingHistoryRequest request) {
        ReadingHistoryResponse response = historyService.record(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/users/me/history/" + response.id())).body(response);
    }

    @org.springframework.web.bind.annotation.DeleteMapping
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> clear(@CurrentUser UUID userId) { history.deleteByUserId(userId); return ResponseEntity.noContent().build(); }

    @GetMapping
    public PageResponse<ReadingHistoryResponse> list(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return historyService.list(userId, page, size);
    }
}
