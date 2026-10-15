package com.pulseai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.flywaydb.core.Flyway;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Flyway flyway;

    @Value("${spring.flyway.target}")
    private String expectedFlywayTarget;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsApplicationEnvironmentAndRequestId() {
        var response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"status\":\"UP\"")
                .contains("\"application\":\"pulseai-backend\"")
                .contains("\"environment\":\"test\"");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(response.getBody()).contains(response.getHeaders().getFirst("X-Request-Id"));
    }

    @Test
    void unimplementedApiRoutesAreDeniedWithStructuredErrors() {
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/api/not-implemented",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody())
                .contains("\"status\":401")
                .contains("\"error\":\"Unauthorized\"")
                .contains("\"path\":\"/api/not-implemented\"")
                .contains("\"requestId\"");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    void authenticatedRequestsToUnimplementedRoutesReceiveForbiddenResponse() throws Exception {
        mockMvc.perform(get("/api/not-implemented").with(user("test-reader")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void databaseHealthActuatorIsNotPubliclyExposedThroughTheApi() {
        var response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void configuredCorsOriginCanCallHealthEndpoint() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5180");
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/api/health",
                HttpMethod.OPTIONS,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5180");
    }

    @Test
    void corsRejectsUnconfiguredOrigins() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://untrusted.example");
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        var response = restTemplate.exchange(
                "http://localhost:" + port + "/api/health",
                HttpMethod.OPTIONS,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    void flywayRunsTheVersionedDomainMigrations() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(expectedFlywayTarget);
    }

    @Test
    void passwordEncoderUsesOneWayBcryptHashing() {
        String encoded = passwordEncoder.encode("example-password");

        assertThat(encoded).startsWith("$2");
        assertThat(encoded).isNotEqualTo("example-password");
        assertThat(passwordEncoder.matches("example-password", encoded)).isTrue();
    }
}
