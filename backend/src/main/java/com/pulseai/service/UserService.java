package com.pulseai.service;

import java.util.UUID;

import com.pulseai.dto.user.UpdateUserRequest;
import com.pulseai.dto.user.UserResponse;
import com.pulseai.exception.ResourceNotFoundException;
import com.pulseai.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
    }

    @Transactional
    public UserResponse updateCurrentUser(UUID userId, UpdateUserRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        user.setDisplayName(request.name().trim());
        userRepository.flush();
        return userMapper.toUserResponse(user);
    }
}
