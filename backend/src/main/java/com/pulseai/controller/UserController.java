package com.pulseai.controller;

import com.pulseai.dto.user.UpdateUserRequest;
import com.pulseai.dto.user.UserResponse;
import com.pulseai.security.CurrentUser;
import com.pulseai.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse getCurrentUser(@CurrentUser java.util.UUID userId) {
        return userService.getCurrentUser(userId);
    }

    @PutMapping
    public UserResponse updateCurrentUser(
            @CurrentUser java.util.UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateCurrentUser(userId, request);
    }
}
