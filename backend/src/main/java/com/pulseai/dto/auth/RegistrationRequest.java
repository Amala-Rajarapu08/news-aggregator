package com.pulseai.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters.")
        String name,
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        String email,
        @NotBlank(message = "Password is required.")
        @Size(min = 12, max = 64, message = "Password must be between 12 and 64 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$",
                message = "Password must include lowercase, uppercase, number, and symbol characters.")
        String password) {
    @Override public String toString() { return "RegistrationRequest[credentials=REDACTED]"; }
}
