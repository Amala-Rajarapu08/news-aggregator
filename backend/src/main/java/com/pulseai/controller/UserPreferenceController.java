package com.pulseai.controller;

import java.util.UUID;

import com.pulseai.dto.preference.UpdateUserPreferencesRequest;
import com.pulseai.dto.preference.UserPreferencesResponse;
import com.pulseai.security.CurrentUser;
import com.pulseai.service.UserPreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class UserPreferenceController {

    private final UserPreferenceService preferenceService;

    public UserPreferenceController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public UserPreferencesResponse get(@CurrentUser UUID userId) {
        return preferenceService.get(userId);
    }

    @PutMapping
    public UserPreferencesResponse update(
            @CurrentUser UUID userId,
            @Valid @RequestBody UpdateUserPreferencesRequest request) {
        return preferenceService.update(userId, request);
    }
}
