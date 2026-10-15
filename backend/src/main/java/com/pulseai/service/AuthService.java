package com.pulseai.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.pulseai.dto.auth.AuthResponse;
import com.pulseai.dto.auth.LoginRequest;
import com.pulseai.dto.auth.RegistrationRequest;
import com.pulseai.entity.User;
import com.pulseai.entity.UserPreference;
import com.pulseai.exception.DuplicateEmailException;
import com.pulseai.repository.UserPreferenceRepository;
import com.pulseai.repository.UserRepository;
import com.pulseai.security.JwtTokenService;
import com.pulseai.security.PulseUserDetails;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;
    private final BrowserSessionService sessions;

    public AuthService(
            UserRepository userRepository,
            UserPreferenceRepository preferenceRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            UserMapper userMapper, BrowserSessionService sessions) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
        this.sessions = sessions;
    }

    @Transactional
    public BrowserSessionService.Result register(RegistrationRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 bytes when encoded as UTF-8.");
        }

        User user = userRepository.saveAndFlush(
                new User(request.name().trim(), email, passwordEncoder.encode(request.password())));
        preferenceRepository.save(new UserPreference(user));
        return sessions.create(user);
    }

    @Transactional
    public BrowserSessionService.Result login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        PulseUserDetails principal = (PulseUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated account was not found."));
        return sessions.create(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
