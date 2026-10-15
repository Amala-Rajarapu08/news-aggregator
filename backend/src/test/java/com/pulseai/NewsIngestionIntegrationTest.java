package com.pulseai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulseai.ingestion.NewsIngestionService;
import com.pulseai.ingestion.RssFeedFetcher;
import com.pulseai.ingestion.RssFeedResponse;
import com.pulseai.service.IngestionStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.news.rss-feeds=https://feed.example.test/rss",
        "app.news.image-metadata-enabled=false"
})
class NewsIngestionIntegrationTest {

    private static final String FEED_URL = "https://feed.example.test/rss";
    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Integration Journal</title>
                <link>https://journal.example/</link>
                <language>en</language>
                <item>
                  <guid>integration-article-1</guid>
                  <title>Verified integration headline</title>
                  <description>Source supplied summary.</description>
                  <link>https://journal.example/news/article-1?utm_source=feed</link>
                  <pubDate>Mon, 05 Oct 2026 08:30:00 GMT</pubDate>
                  <category>Technology</category>
                </item>
                <item>
                  <title>Incomplete item is skipped</title>
                  <link>https://journal.example/news/incomplete</link>
                </item>
              </channel>
            </rss>
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private NewsIngestionService ingestionService;

    @Autowired
    private IngestionStatusService ingestionStatusService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private RssFeedFetcher feedFetcher;

    @BeforeEach
    void cleanDatabase() throws Exception {
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
        when(feedFetcher.fetchWithStatus(FEED_URL)).thenReturn(new RssFeedResponse(
                200, RSS.replace("Mon, 05 Oct 2026 08:30:00 GMT", java.time.Instant.now().minusSeconds(60).toString())));
    }

    @Test
    void ingestsRealisticFeedDataDeduplicatesAndServesArticlesSourcesAndCategories() {
        var firstRun = ingestionService.ingest();
        var secondRun = ingestionService.ingest();

        assertThat(firstRun.fetched()).isEqualTo(2);
        assertThat(firstRun.accepted()).isEqualTo(1);
        assertThat(firstRun.inserted()).isEqualTo(1);
        assertThat(firstRun.invalidSkipped()).isEqualTo(1);
        assertThat(firstRun.failures()).isZero();
        assertThat(secondRun.inserted()).isZero();
        assertThat(secondRun.duplicatesSkipped()).isEqualTo(1);
        assertThat(secondRun.invalidSkipped()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM articles", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sources", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT c.slug FROM articles a JOIN categories c ON c.id = a.category_id",
                String.class)).isEqualTo("technology");

        var articles = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/articles?page=0&size=10",
                JsonNode.class);
        var filteredArticles = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/articles?page=0&size=10&category=technology",
                JsonNode.class);
        var sources = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/sources",
                JsonNode.class);
        var sourceId = sources.getBody().path("items").get(0).path("id").asText();
        var articleId = articles.getBody().path("items").get(0).path("id").asText();
        var articleDetail = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/articles/" + articleId,
                JsonNode.class);
        var sourceDetail = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/sources/" + sourceId,
                JsonNode.class);
        var categories = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/categories",
                JsonNode.class);
        var sourceFilteredArticles = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/articles?page=0&size=10&sourceId=" + sourceId,
                JsonNode.class);
        var combinedFilteredArticles = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/articles?page=0&size=10&sourceId=" + sourceId + "&category=TECHNOLOGY&q=journal",
                JsonNode.class);
        var publisherSearch = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/articles?page=0&size=10&q=journal",
                JsonNode.class);
        var semanticFallback = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/search/semantic",
                java.util.Map.of("query", "Verified integration"),
                JsonNode.class);

        assertThat(articles.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(articles.getBody().path("items")).hasSize(1);
        assertThat(articles.getBody().path("items").get(0).path("url").asText())
                .isEqualTo("https://journal.example/news/article-1?utm_source=feed");
        assertThat(filteredArticles.getBody().path("items")).hasSize(1);
        assertThat(sourceFilteredArticles.getBody().path("items")).hasSize(1);
        assertThat(combinedFilteredArticles.getBody().path("items")).hasSize(1);
        assertThat(publisherSearch.getBody().path("items")).hasSize(1);
        assertThat(semanticFallback.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(semanticFallback.getBody().path("mode").asText()).isEqualTo("keyword-fallback");
        assertThat(semanticFallback.getBody().path("reason").asText()).isEqualTo("pgvector-unavailable");
        assertThat(semanticFallback.getBody().path("items")).hasSize(1);
        assertThat(sources.getBody().path("items")).hasSize(1);
        assertThat(sources.getBody().path("items").get(0).path("name").asText()).isEqualTo("Integration Journal");
        assertThat(articleDetail.getBody().path("title").asText()).isEqualTo("Verified integration headline");
        assertThat(sourceDetail.getBody().path("domain").asText()).isEqualTo("journal.example");
        assertThat(categories.getBody().toString()).contains("\"slug\":\"technology\"");
    }

    @Test
    void supportsEveryCatalogCategoryAndReturnsEmptyPublishersAsAValidResponse() throws Exception {
        List<String> categorySlugs = List.of(
                "business", "entertainment", "general", "health", "politics", "science", "sports", "technology", "world");
        Map<String, String> categoryNames = Map.of(
                "business", "Business",
                "entertainment", "Entertainment",
                "general", "General",
                "health", "Health",
                "politics", "Politics",
                "science", "Science",
                "sports", "Sports",
                "technology", "Technology",
                "world", "World");

        jdbcTemplate.update("""
                INSERT INTO categories (id, name, slug, created_at, updated_at)
                SELECT ?, 'General', 'general', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug='general')
                """, java.util.UUID.fromString("00000000-0000-0000-0000-000000000309"));

        var emptyPublishers = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/sources/available?category=business", JsonNode.class);
        assertThat(emptyPublishers.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(emptyPublishers.getBody()).isEmpty();

        String items = categorySlugs.stream().map(slug -> """
                <item>
                  <guid>category-%s</guid>
                  <title>%s category test report</title>
                  <description>Publisher supplied test summary.</description>
                  <link>https://journal.example/%s</link>
                  <pubDate>%s</pubDate>
                  <category>%s</category>
                </item>
                """.formatted(slug, categoryNames.get(slug), slug,
                        java.time.Instant.now().minusSeconds(60), categoryNames.get(slug)))
                .collect(Collectors.joining("\n"));
        String categorizedFeed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>Integration Journal</title>
                <link>https://journal.example/</link><language>en</language>%s</channel></rss>
                """.formatted(items);
        when(feedFetcher.fetchWithStatus(FEED_URL)).thenReturn(new RssFeedResponse(200, categorizedFeed));

        assertThat(ingestionService.ingest().inserted()).isEqualTo(categorySlugs.size());
        var allArticles = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/articles?page=0&size=20", JsonNode.class);
        assertThat(allArticles.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(allArticles.getBody().path("totalItems").asInt()).isEqualTo(categorySlugs.size());

        var allPublishers = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/sources/available", JsonNode.class);
        var searchedPublishers = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/sources/available?q=integration", JsonNode.class);
        assertThat(allPublishers.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(allPublishers.getBody()).hasSize(1);
        assertThat(searchedPublishers.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(searchedPublishers.getBody()).hasSize(1);

        for (String slug : categorySlugs) {
            var articles = restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/v1/articles?category=" + slug + "&page=0&size=20",
                    JsonNode.class);
            var publishers = restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/v1/sources/available?category=" + slug,
                    JsonNode.class);

            assertThat(articles.getStatusCode().is2xxSuccessful()).as("article filter for %s", slug).isTrue();
            assertThat(articles.getBody().path("items")).as("articles for %s", slug).hasSize(1);
            assertThat(articles.getBody().path("items").get(0).path("category").asText())
                    .isEqualTo(categoryNames.get(slug));
            assertThat(publishers.getStatusCode().is2xxSuccessful()).as("publisher filter for %s", slug).isTrue();
            assertThat(publishers.getBody()).as("publishers for %s", slug).hasSize(1);
            assertThat(publishers.getBody().get(0).path("name").asText()).isEqualTo("Integration Journal");
        }
    }

    @Test
    void listsEveryPublisherWithArticlesInASelectedCategory() {
        java.time.Instant now = java.time.Instant.now().minusSeconds(60);
        java.sql.Timestamp timestamp = java.sql.Timestamp.from(now);
        java.util.UUID firstSource = java.util.UUID.randomUUID();
        java.util.UUID secondSource = java.util.UUID.randomUUID();
        java.util.UUID businessCategory = java.util.UUID.fromString("00000000-0000-0000-0000-000000000301");

        for (var source : List.of(
                new Object[] { firstSource, "Business Desk One", "one.business.example", "https://one.business.example/" },
                new Object[] { secondSource, "Business Desk Two", "two.business.example", "https://two.business.example/" })) {
            jdbcTemplate.update(
                    "INSERT INTO sources (id,name,domain,base_url,active,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                    source[0], source[1], source[2], source[3], true, timestamp, timestamp);
            jdbcTemplate.update(
                    "INSERT INTO articles (id,title,description,canonical_url,original_url,image_url,published_at,language,source_id,category_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    java.util.UUID.randomUUID(), source[1] + " market update", "Business coverage", source[3] + "story",
                    source[3] + "story", null, timestamp, "en", source[0], businessCategory, timestamp, timestamp);
        }

        var publishers = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/sources/available?category=business", JsonNode.class);

        assertThat(publishers.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(publishers.getBody()).hasSize(2);
        assertThat(publishers.getBody().findValuesAsText("name"))
                .containsExactly("Business Desk One", "Business Desk Two");
    }

    @Test
    void duplicateFeedItemBackfillsOnlyAMissingPublisherImage() throws Exception {
        var firstRun = ingestionService.ingest();
        assertThat(firstRun.inserted()).isEqualTo(1);
        assertThat(firstRun.imagesBackfilled()).isZero();

        String imageFeed = RSS
                .replace("Mon, 05 Oct 2026 08:30:00 GMT", java.time.Instant.now().minusSeconds(60).toString())
                .replace("<category>Technology</category>", "<category>Technology</category><enclosure url=\"https://journal.example/images/article-1.jpg\" type=\"image/jpeg\"/>");
        when(feedFetcher.fetchWithStatus(FEED_URL)).thenReturn(new RssFeedResponse(200, imageFeed));

        var secondRun = ingestionService.ingest();
        assertThat(secondRun.inserted()).isZero();
        assertThat(secondRun.duplicatesSkipped()).isEqualTo(1);
        assertThat(secondRun.imagesBackfilled()).isEqualTo(1);
        assertThat(secondRun.feedReports()).singleElement()
                .extracting(com.pulseai.ingestion.FeedIngestionReport::imagesBackfilled)
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT image_url FROM articles", String.class))
                .isEqualTo("https://journal.example/images/article-1.jpg");

        var thirdRun = ingestionService.ingest();
        assertThat(thirdRun.imagesBackfilled()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT image_url FROM articles", String.class))
                .isEqualTo("https://journal.example/images/article-1.jpg");
    }

    @Test
    void persistsAndReturnsFeedImagesAndOnlyOffersPublishersWithMatchingArticles() throws Exception {
        String imageUrl = "https://journal.example/images/article-1.jpg";
        String imageFeed = RSS
                .replace("Mon, 05 Oct 2026 08:30:00 GMT", java.time.Instant.now().minusSeconds(60).toString())
                .replace("<category>Technology</category>", "<category>Technology</category>"
                        + "<media:thumbnail xmlns:media=\"http://search.yahoo.com/mrss/\" url=\"" + imageUrl + "\"/>");
        when(feedFetcher.fetchWithStatus(FEED_URL)).thenReturn(new RssFeedResponse(200, imageFeed));

        assertThat(ingestionService.ingest().inserted()).isEqualTo(1);
        var articles = restTemplate.getForEntity("http://localhost:" + port + "/api/v1/articles?page=0&size=10", JsonNode.class);
        var articleId = articles.getBody().path("items").get(0).path("id").asText();
        var detail = restTemplate.getForEntity("http://localhost:" + port + "/api/v1/articles/" + articleId, JsonNode.class);
        var technologySources = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/sources/available?category=technology", JsonNode.class);
        var businessSources = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/sources/available?category=business", JsonNode.class);

        assertThat(jdbcTemplate.queryForObject("SELECT image_url FROM articles", String.class)).isEqualTo(imageUrl);
        assertThat(articles.getBody().path("items").get(0).path("imageUrl").asText()).isEqualTo(imageUrl);
        assertThat(detail.getBody().path("imageUrl").asText()).isEqualTo(imageUrl);
        assertThat(technologySources.getBody()).hasSize(1);
        assertThat(technologySources.getBody().get(0).path("name").asText()).isEqualTo("Integration Journal");
        assertThat(businessSources.getBody()).isEmpty();
    }

    @Test
    void persistsPerFeedDiagnosticsAndImageBackfillCountInTheStatusRecord() {
        var report = ingestionService.ingest();
        var status = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/news/status", JsonNode.class);

        assertThat(status.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(java.time.Instant.parse(status.getBody().path("lastAttemptAt").asText()))
                .isCloseTo(report.completedAt(), org.assertj.core.api.Assertions.within(1,
                        java.time.temporal.ChronoUnit.MILLIS));
        assertThat(status.getBody().path("fetched").asInt()).isEqualTo(report.fetched());
        assertThat(status.getBody().path("imagesBackfilled").asInt()).isEqualTo(report.imagesBackfilled());
        assertThat(status.getBody().path("feeds")).hasSize(1);
        assertThat(status.getBody().path("feeds").get(0).path("feedUrl").asText()).isEqualTo("https://feed.example.test/");
        assertThat(status.getBody().path("feeds").get(0).path("httpStatus").asInt()).isEqualTo(200);
        assertThat(status.getBody().path("feeds").get(0).path("parsedItems").asInt()).isEqualTo(2);
    }

    @Test
    void recordsPartialProviderSuccessButKeepsTheLastSuccessWhenEveryProviderFails() {
        java.time.Instant previousSuccess = java.time.Instant.now().minusSeconds(60);
        jdbcTemplate.update("UPDATE ingestion_status SET last_success_at=? WHERE id=1",
                java.sql.Timestamp.from(previousSuccess));

        java.time.Instant partialCompletion = java.time.Instant.now();
        ingestionStatusService.record(new com.pulseai.ingestion.IngestionReport(
                partialCompletion.minusSeconds(1), partialCompletion, 3, 3, 1, 0, 0, 0, 1,
                List.of(
                        new com.pulseai.ingestion.IngestionReport.ProviderReport("news-api", 3, 0),
                        new com.pulseai.ingestion.IngestionReport.ProviderReport("gnews", 0, 1)),
                List.of()));

        java.time.Instant persistedSuccess = jdbcTemplate.queryForObject(
                "SELECT last_success_at FROM ingestion_status WHERE id=1", java.sql.Timestamp.class).toInstant();
        assertThat(persistedSuccess).isCloseTo(partialCompletion,
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));

        java.time.Instant failedCompletion = partialCompletion.plusSeconds(2);
        ingestionStatusService.record(new com.pulseai.ingestion.IngestionReport(
                failedCompletion.minusSeconds(1), failedCompletion, 0, 0, 0, 0, 0, 0, 2,
                List.of(
                        new com.pulseai.ingestion.IngestionReport.ProviderReport("news-api", 0, 1),
                        new com.pulseai.ingestion.IngestionReport.ProviderReport("gnews", 0, 1)),
                List.of()));

        java.time.Instant stillLastSuccess = jdbcTemplate.queryForObject(
                "SELECT last_success_at FROM ingestion_status WHERE id=1", java.sql.Timestamp.class).toInstant();
        assertThat(stillLastSuccess).isCloseTo(partialCompletion,
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void manualIngestionRequiresAdministratorAuthority() throws Exception {
        String registration = """
                {"name":"Integration Admin","email":"admin@example.test","password":"GoodPassword9!"}
                """;
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        JsonNode registered = restTemplate.postForObject(
                "http://localhost:" + port + "/api/v1/auth/register",
                new HttpEntity<>(registration, jsonHeaders),
                JsonNode.class);
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(registered.path("accessToken").asText());
        var forbidden = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/ingestion/run",
                HttpMethod.POST,
                new HttpEntity<>(null, userHeaders),
                JsonNode.class);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        UUID userId = UUID.fromString(registered.path("user").path("id").asText());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
        String login = """
                {"email":"admin@example.test","password":"GoodPassword9!"}
                """;
        JsonNode auth = restTemplate.postForObject(
                "http://localhost:" + port + "/api/v1/auth/login",
                new HttpEntity<>(login, jsonHeaders),
                JsonNode.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(auth.path("accessToken").asText());

        var report = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/ingestion/run",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                JsonNode.class);

        assertThat(report.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(report.getBody().path("inserted").asInt()).isEqualTo(1);
    }
}
