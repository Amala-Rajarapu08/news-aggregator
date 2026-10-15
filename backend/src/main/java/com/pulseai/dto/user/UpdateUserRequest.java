package com.pulseai.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters.")
        String name) {
}
