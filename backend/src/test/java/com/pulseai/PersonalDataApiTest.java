package com.pulseai;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PersonalDataApiTest {

    private static final String API = "/api/v1";
    private static final String PASSWORD = "GoodPassword9!";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void savedArticlesAreUniquePagedAndPrivateToTheAuthenticatedUser() {
        UUID articleId = insertArticle();
        String firstUser = createUser("first@example.test");
        String secondUser = createUser("second@example.test");

        var saveResponse = request(firstUser, HttpMethod.POST, API + "/users/me/saved/" + articleId, null);
        var duplicateResponse = request(firstUser, HttpMethod.POST, API + "/users/me/saved/" + articleId, null);
        var firstList = request(firstUser, HttpMethod.GET, API + "/users/me/saved?page=0&size=10", null);
        var secondList = request(secondUser, HttpMethod.GET, API + "/users/me/saved", null);

        assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(firstList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstList.getBody().path("items")).hasSize(1);
        assertThat(firstList.getBody().path("items").get(0).path("article").path("id").asText())
                .isEqualTo(articleId.toString());
        assertThat(secondList.getBody().path("items")).isEmpty();

        var removed = request(firstUser, HttpMethod.DELETE, API + "/users/me/saved/" + articleId, null);
        var afterRemove = request(firstUser, HttpMethod.GET, API + "/users/me/saved", null);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(afterRemove.getBody().path("items")).isEmpty();
    }

    @Test
    void savedArticleRejectsMissingCatalogItems() {
        String token = createUser("reader@example.test");
        var missing = request(token, HttpMethod.POST, API + "/users/me/saved/" + UUID.randomUUID(), null);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void readingHistoryIsPagedScopedAndUsesAuthenticatedOwner() {
        UUID articleId = insertArticle();
        String firstUser = createUser("first@example.test");
        String secondUser = createUser("second@example.test");
        String body = """
                {"articleId":"%s","durationSeconds":90,"completed":true,"scrollPercentage":88}
                """.formatted(articleId);

        var withoutConsent = request(firstUser, HttpMethod.POST, API + "/users/me/history", body);
        assertThat(withoutConsent.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        request(firstUser, HttpMethod.PUT, API + "/users/me/preferences", "{\"briefingFrequency\":\"DAILY\",\"historyEnabled\":true,\"topicIds\":[]}");
        var recorded = request(firstUser, HttpMethod.POST, API + "/users/me/history", body);
        var firstList = request(firstUser, HttpMethod.GET, API + "/users/me/history", null);
        var secondList = request(secondUser, HttpMethod.GET, API + "/users/me/history", null);

        assertThat(recorded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(recorded.getBody().path("durationSeconds").asInt()).isEqualTo(90);
        assertThat(recorded.getBody().path("article").path("id").asText()).isEqualTo(articleId.toString());
        assertThat(firstList.getBody().path("items")).hasSize(1);
        assertThat(secondList.getBody().path("items")).isEmpty();
    }

    @Test
    void userPreferencesCanBeReadAndUpdatedWithExplicitTopicChoices() {
        UUID topicId = insertTopic();
        String token = createUser("reader@example.test");
        var initial = request(token, HttpMethod.GET, API + "/users/me/preferences", null);
        var update = request(token, HttpMethod.PUT, API + "/users/me/preferences", """
                {"briefingFrequency":"WEEKLY","recommendationsEnabled":false,"notificationsEnabled":true,"topicIds":["%s"]}
                """.formatted(topicId));
        var loaded = request(token, HttpMethod.GET, API + "/users/me/preferences", null);

        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loaded.getBody().path("briefingFrequency").asText()).isEqualTo("WEEKLY");
        assertThat(loaded.getBody().path("recommendationsEnabled").asBoolean()).isFalse();
        assertThat(loaded.getBody().path("topicIds").get(0).asText()).isEqualTo(topicId.toString());

        var invalidTopic = request(token, HttpMethod.PUT, API + "/users/me/preferences", """
                {"briefingFrequency":"DAILY","recommendationsEnabled":true,"notificationsEnabled":true,"topicIds":["%s"]}
                """.formatted(UUID.randomUUID()));
        assertThat(invalidTopic.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void profileUpdateDoesNotAcceptOrExposeAnotherUserId() {
        String token = createUser("reader@example.test");
        var updated = request(token, HttpMethod.PUT, API + "/users/me", "{\"name\":\"Updated Reader\"}");

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().path("name").asText()).isEqualTo("Updated Reader");
        assertThat(updated.getBody().toString()).doesNotContain("passwordHash", "password_hash");
    }

    private UUID insertArticle() {
        UUID sourceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO sources (id, name, domain, base_url, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                sourceId, "Test Publisher", "publisher.example",
                "https://publisher.example", true, now, now);
        jdbcTemplate.update(
                """
                INSERT INTO articles
                    (id, title, canonical_url, original_url, published_at, language, source_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                articleId, "A real test article", "https://publisher.example/article", "https://publisher.example/article",
                now, "en", sourceId, now, now);
        return articleId;
    }

    private UUID insertTopic() {
        UUID topicId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO topics (id, name, slug, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                topicId, "Climate research", "climate-research", now, now);
        return topicId;
    }

    private String createUser(String email) {
        String body = """
                {"name":"Pulse Reader","email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
        var response = restTemplate.postForEntity(
                url(API + "/auth/register"),
                new HttpEntity<>(body, jsonHeaders()),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().path("accessToken").asText();
    }

    private org.springframework.http.ResponseEntity<JsonNode> request(
            String token,
            HttpMethod method,
            String path,
            String body) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(
                url(path),
                method,
                new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
