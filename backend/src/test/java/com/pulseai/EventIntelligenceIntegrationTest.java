package com.pulseai;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulseai.service.EventIntelligenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EventIntelligenceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventIntelligenceService eventService;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void cleanEventData() {
        jdbc.update("DELETE FROM event_articles");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM articles");
        jdbc.update("DELETE FROM sources");
    }

    @Test
    void backfillIsIdempotentAndPublicFeedAndDetailReturnArticleEvidence() {
        UUID categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug='world'", UUID.class);
        UUID firstSource = source("First Publisher", "first.publisher.test");
        UUID secondSource = source("Second Publisher", "second.publisher.test");
        Instant firstPublished = Instant.now().minusSeconds(3600);
        Instant secondPublished = Instant.now().minusSeconds(1800);
        UUID firstArticle = article(firstSource, categoryId,
                "Six Flags rollercoaster incident leaves visitor dead",
                "Police say a visitor died after falling from a ride.", firstPublished, "first");
        UUID secondArticle = article(secondSource, categoryId,
                "Witnesses describe Six Flags rollercoaster accident",
                "A person died and others were injured at the Georgia park.", secondPublished, "second");
        article(firstSource, categoryId, "A separate World report concerns a court hearing",
                "The proceedings are unrelated to the amusement park incident.", secondPublished, "separate");

        var firstRun = eventService.backfill();
        var secondRun = eventService.backfill();

        assertThat(firstRun.articlesEvaluated()).isEqualTo(3);
        assertThat(firstRun.eventsGenerated()).isEqualTo(1);
        assertThat(firstRun.multiArticleEvents()).isEqualTo(1);
        assertThat(firstRun.articleRelationshipsCreated()).isEqualTo(2);
        assertThat(secondRun.eventsGenerated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_articles", Integer.class)).isEqualTo(2);

        JsonNode feed = restTemplate.getForObject("http://localhost:" + port + "/api/v1/events", JsonNode.class);
        assertThat(feed.path("totalItems").asInt()).isEqualTo(1);
        JsonNode event = feed.path("items").get(0);
        assertThat(event.path("articleCount").asInt()).isEqualTo(2);
        assertThat(event.path("sourceCount").asInt()).isEqualTo(2);
        assertThat(event.path("sources").findValuesAsText("name"))
                .containsExactly("First Publisher", "Second Publisher");

        JsonNode detail = restTemplate.getForObject(
                "http://localhost:" + port + "/api/v1/events/" + event.path("id").asText(), JsonNode.class);
        assertThat(detail.path("articles")).hasSize(2);
        assertThat(detail.path("articles").get(0).path("id").asText()).isEqualTo(firstArticle.toString());
        assertThat(detail.path("articles").get(0).path("url").asText()).isEqualTo("https://first.example/story");
        assertThat(detail.path("description").asText()).contains("First Publisher:", "Second Publisher:");
    }

    private UUID source(String name, String domain) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO sources (id, name, domain, base_url, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, TRUE, ?, ?)
                """, id, name, domain, "https://" + domain + "/", now, now);
        return id;
    }

    private UUID article(UUID sourceId, UUID categoryId, String title, String description,
            Instant publishedAt, String slug) {
        UUID id = UUID.randomUUID();
        Timestamp published = Timestamp.from(publishedAt);
        jdbc.update("""
                INSERT INTO articles (id, title, description, canonical_url, original_url, published_at,
                    language, source_id, category_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'en', ?, ?, ?, ?)
                """, id, title, description, "https://" + slug + ".example/story",
                "https://" + slug + ".example/story", published, sourceId, categoryId, published, published);
        return id;
    }
}
