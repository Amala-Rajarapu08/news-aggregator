package com.pulseai.controller;

import java.time.Instant;

import com.pulseai.dto.health.HealthResponse;
import com.pulseai.security.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String applicationName;
    private final String environment;

    public HealthController(
            @Value("${spring.application.name}") String applicationName,
            @Value("${app.environment:local}") String environment) {
        this.applicationName = applicationName;
        this.environment = environment;
    }

    @GetMapping
    public HealthResponse getHealth(HttpServletRequest request) {
        return new HealthResponse(
                "UP",
                applicationName,
                environment,
                Instant.now(),
                RequestCorrelationFilter.requestId(request));
    }
}
