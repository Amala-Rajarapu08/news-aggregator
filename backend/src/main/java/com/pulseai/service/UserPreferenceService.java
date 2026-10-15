package com.pulseai.service;

import java.util.List;
import java.util.UUID;

import com.pulseai.dto.preference.UpdateUserPreferencesRequest;
import com.pulseai.dto.preference.UserPreferencesResponse;
import com.pulseai.entity.BriefingFrequency;
import com.pulseai.entity.UserPreference;
import com.pulseai.entity.UserTopicPreference;
import com.pulseai.exception.ResourceNotFoundException;
import com.pulseai.repository.TopicRepository;
import com.pulseai.repository.UserPreferenceRepository;
import com.pulseai.repository.UserRepository;
import com.pulseai.repository.UserTopicPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final UserTopicPreferenceRepository topicPreferenceRepository;
    private final TopicRepository topicRepository;
    private final com.pulseai.repository.CategoryRepository categories;
    private final UserRepository userRepository;

    public UserPreferenceService(
            UserPreferenceRepository preferenceRepository,
            UserTopicPreferenceRepository topicPreferenceRepository,
            TopicRepository topicRepository,
            UserRepository userRepository, com.pulseai.repository.CategoryRepository categories) {
        this.preferenceRepository = preferenceRepository;
        this.topicPreferenceRepository = topicPreferenceRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public UserPreferencesResponse get(UUID userId) {
        var preference = preferenceRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User preferences were not found."));
        List<UUID> topicIds = topicPreferenceRepository.findByUser_IdOrderByTopic_NameAsc(userId).stream()
                .map(topicPreference -> topicPreference.getTopic().getId())
                .toList();
        return toResponse(preference, topicIds);
    }

    @Transactional
    public UserPreferencesResponse update(UUID userId, UpdateUserPreferencesRequest request) {
        var preference = preferenceRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User preferences were not found."));
        List<UUID> topicIds = request.topicIds() == null ? List.of() : request.topicIds().stream().distinct().toList();
        var topics = topicRepository.findAllById(topicIds);
        if (topics.size() != topicIds.size()) {
            throw new ResourceNotFoundException("One or more topics were not found.");
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));

        var selected = request.categorySlugs() == null ? List.<String>of() : request.categorySlugs().stream().distinct().toList();
        if (selected.stream().anyMatch(slug -> categories.findBySlugIgnoreCase(slug).isEmpty())) throw new IllegalArgumentException("Unknown category.");
        preference.updateDataControls(request.historyEnabled(), selected);
        preference.update(
                BriefingFrequency.valueOf(request.briefingFrequency().name()),
                request.recommendationsEnabled(),
                request.notificationsEnabled());
        topicPreferenceRepository.deleteByUser_Id(userId);
        topicPreferenceRepository.flush();
        topicPreferenceRepository.saveAll(topics.stream()
                .map(topic -> new UserTopicPreference(user, topic))
                .toList());
        preferenceRepository.flush();
        return toResponse(preference, topicIds);
    }

    private UserPreferencesResponse toResponse(UserPreference preference, List<UUID> topicIds) {
        return new UserPreferencesResponse(
                preference.getBriefingFrequency().name(),
                preference.isRecommendationsEnabled(),
                preference.isNotificationsEnabled(),
                topicIds,
                preference.getUpdatedAt(), preference.isHistoryEnabled(), preference.getCategorySlugs().stream().sorted().toList());
    }
}
