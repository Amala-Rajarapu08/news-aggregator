package com.pulseai.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulseai.security.RequestCorrelationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestCorrelationFilter())
                .build();
    }

    @Test
    void validationFailureReturnsStructuredFieldErrors() throws Exception {
        mockMvc.perform(post("/validation-probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("One or more request fields are invalid."))
                .andExpect(jsonPath("$.path").value("/validation-probe"))
                .andExpect(jsonPath("$.validationErrors.name").value("Name is required."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void malformedJsonReturnsSafeClientError() throws Exception {
        mockMvc.perform(post("/validation-probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is missing or malformed."))
                .andExpect(jsonPath("$.validationErrors").isEmpty());
    }

    @Test
    void unexpectedFailuresDoNotExposeInternalMessages() throws Exception {
        mockMvc.perform(get("/failure-probe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @RestController
    static class ValidationProbeController {

        @PostMapping("/validation-probe")
        void validate(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/failure-probe")
        void fail() {
            throw new IllegalStateException("internal database secret");
        }
    }

    record ValidationRequest(@NotBlank(message = "Name is required.") String name) {
    }
}
