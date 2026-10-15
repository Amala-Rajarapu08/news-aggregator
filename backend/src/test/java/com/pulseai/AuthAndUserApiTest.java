package com.pulseai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthAndUserApiTest {

    private static final String API = "/api/v1";
    private static final String PASSWORD = "GoodPassword9!";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM saved_articles");
        jdbcTemplate.update("DELETE FROM reading_history");
        jdbcTemplate.update("DELETE FROM user_topic_preferences");
        jdbcTemplate.update("DELETE FROM user_preferences");
        jdbcTemplate.update("DELETE FROM event_articles");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM article_topics");
        jdbcTemplate.update("DELETE FROM articles");
        jdbcTemplate.update("DELETE FROM topics");
        jdbcTemplate.update("DELETE FROM sources");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void registersNormalizesEmailHashesPasswordAndReturnsOnlySafeUserFields() throws Exception {
        var response = register("Reader@Example.test", PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String refreshCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(refreshCookie)
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(response.getBody().path("accessToken").asText()).isNotBlank();
        assertThat(response.getBody().path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.getBody().path("user").path("email").asText()).isEqualTo("reader@example.test");
        assertThat(response.getBody().toString()).doesNotContain("passwordHash", "password_hash");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?",
                String.class,
                "reader@example.test")).startsWith("$2");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_preferences",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateEmailWithConflictAndValidationFailuresWithBadRequest() {
        register("reader@example.test", PASSWORD);
        var duplicate = register("READER@example.test", PASSWORD);
        var weakPassword = register("second@example.test", "weak");
        var invalidEmail = register("not-an-email", PASSWORD);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(weakPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loginReturnsJwtAndCurrentUserRouteUsesTokenSubject() throws Exception {
        register("reader@example.test", PASSWORD);
        var login = login("reader@example.test", PASSWORD);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = login.getBody().path("accessToken").asText();
        var profile = authorized(token, HttpMethod.GET, API + "/users/me", null);

        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody().path("email").asText()).isEqualTo("reader@example.test");
        assertThat(profile.getBody().toString()).doesNotContain("passwordHash", "password_hash");
    }

    @Test
    void invalidCredentialsAndUnauthenticatedPrivateAccessAreRejected() {
        register("reader@example.test", PASSWORD);
        assertThat(login("reader@example.test", "WrongPassword9!").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("missing@example.test", PASSWORD).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        var privateResponse = restTemplate.getForEntity(url(API + "/users/me"), String.class);
        assertThat(privateResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authEndpointsDistinguishAllowedOriginsFromUntrustedOrigins() {
        HttpHeaders allowedHeaders = new HttpHeaders();
        allowedHeaders.set("Origin", "http://localhost:5180");
        var allowedWithoutSession = restTemplate.exchange(
                url(API + "/auth/refresh"),
                HttpMethod.POST,
                new HttpEntity<>(allowedHeaders),
                String.class);

        HttpHeaders untrustedHeaders = new HttpHeaders();
        untrustedHeaders.set("Origin", "https://untrusted.example");
        var rejectedOrigin = restTemplate.exchange(
                url(API + "/auth/refresh"),
                HttpMethod.POST,
                new HttpEntity<>(untrustedHeaders),
                String.class);

        HttpHeaders crossSiteHeaders = new HttpHeaders();
        crossSiteHeaders.set("Origin", "http://localhost:5180");
        crossSiteHeaders.set("Sec-Fetch-Site", "cross-site");
        var rejectedFetchMetadata = restTemplate.exchange(
                url(API + "/auth/refresh"),
                HttpMethod.POST,
                new HttpEntity<>(crossSiteHeaders),
                String.class);

        assertThat(allowedWithoutSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(allowedWithoutSession.getBody())
                .contains("Session expired. Please sign in again.");
        assertThat(rejectedOrigin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejectedOrigin.getBody()).doesNotContain("untrusted.example");
        assertThat(rejectedFetchMetadata.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejectedFetchMetadata.getBody()).contains("You do not have permission");
    }

    @Test
    void malformedAndExpiredBearerTokensAreRejected() throws Exception {
        var malformed = authorized("not-a-jwt", HttpMethod.GET, API + "/users/me", null);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String expiredToken = expiredJwt();
        var expiredResponse = authorized(expiredToken, HttpMethod.GET, API + "/users/me", null);
        assertThat(expiredResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticatedUsersCannotCallAdminRoutes() throws Exception {
        String token = register("reader@example.test", PASSWORD).getBody().path("accessToken").asText();
        var response = authorized(token, HttpMethod.GET, API + "/admin/users", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var ingestion = authorized(token, HttpMethod.POST, API + "/admin/ingestion/run", null);
        assertThat(ingestion.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private org.springframework.http.ResponseEntity<JsonNode> register(String email, String password) {
        String body = """
                {"name":"Pulse Reader","email":"%s","password":"%s"}
                """.formatted(email, password);
        return restTemplate.postForEntity(url(API + "/auth/register"), json(body), JsonNode.class);
    }

    private org.springframework.http.ResponseEntity<JsonNode> login(String email, String password) {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        return restTemplate.postForEntity(url(API + "/auth/login"), json(body), JsonNode.class);
    }

    private HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private org.springframework.http.ResponseEntity<JsonNode> authorized(
            String token,
            HttpMethod method,
            String path,
            String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                url(path),
                method,
                new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private String expiredJwt() {
        var claims = JwtClaimsSet.builder()
                .issuer("pulseai-test")
                .subject("b527c94f-c8c8-4bbb-a41c-b3e020dbff89")
                .issuedAt(java.time.Instant.now().minusSeconds(600))
                .expiresAt(java.time.Instant.now().minusSeconds(300))
                .claim("roles", java.util.List.of("ROLE_USER"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
