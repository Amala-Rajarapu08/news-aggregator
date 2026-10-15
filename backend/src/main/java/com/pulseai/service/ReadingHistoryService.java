package com.pulseai.service;

import java.time.Instant;
import java.util.UUID;

import com.pulseai.dto.common.PageResponse;
import com.pulseai.dto.history.ReadingHistoryRequest;
import com.pulseai.dto.history.ReadingHistoryResponse;
import com.pulseai.entity.ReadingHistory;
import com.pulseai.exception.ResourceNotFoundException;
import com.pulseai.repository.ArticleRepository;
import com.pulseai.repository.ReadingHistoryRepository;
import com.pulseai.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingHistoryService {

    private final ReadingHistoryRepository historyRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final com.pulseai.repository.UserPreferenceRepository preferences;

    public ReadingHistoryService(
            ReadingHistoryRepository historyRepository,
            ArticleRepository articleRepository,
            UserRepository userRepository,
            UserMapper userMapper, com.pulseai.repository.UserPreferenceRepository preferences) {
        this.historyRepository = historyRepository;
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.userMapper = userMapper; this.preferences = preferences;
    }

    @Transactional
    public ReadingHistoryResponse record(UUID userId, ReadingHistoryRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        if (!preferences.findByUserId(userId).map(com.pulseai.entity.UserPreference::isHistoryEnabled).orElse(false))
            throw new org.springframework.security.access.AccessDeniedException("Enable reading history in privacy settings first.");
        var article = articleRepository.findById(request.articleId())
                .orElseThrow(() -> new ResourceNotFoundException("Article was not found."));
        Instant openedAt = request.openedAt() == null ? Instant.now() : request.openedAt();
        if (openedAt.isAfter(Instant.now().plusSeconds(300))) {
            throw new IllegalArgumentException("Opened time cannot be more than five minutes in the future.");
        }
        var history = new ReadingHistory(
                user,
                article,
                openedAt,
                request.durationSeconds(),
                request.completed(),
                request.scrollPercentage());
        return userMapper.toReadingHistoryResponse(historyRepository.saveAndFlush(history));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadingHistoryResponse> list(UUID userId, int page, int size) {
        var result = historyRepository.findByUserId(
                userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt")));
        return PageResponse.from(result, result.getContent().stream()
                .map(userMapper::toReadingHistoryResponse)
                .toList());
    }
}
