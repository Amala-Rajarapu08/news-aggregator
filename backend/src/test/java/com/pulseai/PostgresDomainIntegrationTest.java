package com.pulseai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PostgresDomainIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void postgresRunsAllMigrationsCreatesIndexesAndEnforcesSavedArticleUniqueness() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
        assertThat(jdbcTemplate.queryForObject("SELECT current_setting('server_version_num')", String.class))
                .startsWith("17");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_articles_published_at'",
                Integer.class)).isEqualTo(1);

        String userJson = """
                {"name":"Integration Reader","email":"pg-reader@example.test","password":"GoodPassword9!"}
                """;
        var userResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/register",
                userJson,
                JsonNode.class);
        assertThat(userResponse.getStatusCode().is2xxSuccessful()).isTrue();
        UUID userId = UUID.fromString(userResponse.getBody().path("user").path("id").asText());

        UUID sourceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO sources (id,name,domain,base_url,active,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                sourceId, "Integration Publisher", "integration.example",
                "https://integration.example", true, now, now);
        jdbcTemplate.update(
                """
                INSERT INTO articles
                    (id,title,canonical_url,original_url,published_at,language,source_id,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,
                articleId, "Integration article", "https://integration.example/article", "https://integration.example/article",
                now, "en", sourceId, now, now);

        UUID savedId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO saved_articles (id,user_id,article_id,created_at,article_key,title,canonical_url,publisher,publisher_domain,publisher_url,publisher_id,published_at)
                SELECT ?,?,a.id,?,a.id,a.title,a.canonical_url,s.name,s.domain,s.base_url,s.id,a.published_at
                FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=?
                """,
                savedId, userId, now, articleId);
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO saved_articles (id,user_id,article_id,created_at,article_key,title,canonical_url,publisher,publisher_domain,publisher_url,publisher_id,published_at)
                SELECT ?,?,a.id,?,a.id,a.title,a.canonical_url,s.name,s.domain,s.base_url,s.id,a.published_at
                FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=?
                """,
                UUID.randomUUID(), userId, now, articleId))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
