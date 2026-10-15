package com.pulseai.service;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.pulseai.dto.event.EventArticleResponse;
import com.pulseai.dto.event.EventDetailResponse;
import com.pulseai.dto.event.EventFeedResponse;
import com.pulseai.dto.event.EventPublisherResponse;
import com.pulseai.dto.event.EventResponse;
import com.pulseai.exception.ResourceNotFoundException;
import com.pulseai.service.BaselineEventClusterer.ArticleText;
import com.pulseai.service.BaselineEventClusterer.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventIntelligenceService {

    private static final Logger logger = LoggerFactory.getLogger(EventIntelligenceService.class);
    private static final int MAX_BACKFILL_ARTICLES = 2_000;
    private static final int MAX_DESCRIPTION_LENGTH = 2_000;

    private final JdbcTemplate jdbc;
    private final BaselineEventClusterer clusterer;

    public EventIntelligenceService(JdbcTemplate jdbc, BaselineEventClusterer clusterer) {
        this.jdbc = jdbc;
        this.clusterer = clusterer;
    }

    @Scheduled(
            initialDelayString = "${app.events.backfill-initial-delay-ms:5000}",
            fixedDelayString = "${app.events.backfill-interval-ms:900000}")
    public void scheduledBackfill() {
        try {
            BackfillReport report = backfill();
            logger.info("Event clustering completed evaluatedArticles={} clusters={} multiArticleEvents={} method=deterministic-text-baseline",
                    report.articlesEvaluated(), report.eventsGenerated(), report.multiArticleEvents());
        } catch (RuntimeException exception) {
            logger.warn("Event clustering failed exceptionType={}", exception.getClass().getSimpleName());
        }
    }

    @Transactional
    public synchronized BackfillReport backfill() {
        List<ArticleText> articles = loadArticles();
        List<Cluster> clusters = clusterer.cluster(articles);
        Instant now = Instant.now();
        Set<String> currentKeys = new HashSet<>();
        int linkedArticles = 0;

        for (Cluster cluster : clusters) {
            List<ArticleText> members = cluster.articles().stream()
                    .sorted(Comparator.comparing(ArticleText::publishedAt).thenComparing(ArticleText::id))
                    .toList();
            ArticleText first = members.get(0);
            ArticleText latest = members.get(members.size() - 1);
            String clusterKey = "baseline:" + first.id();
            currentKeys.add(clusterKey);
            UUID eventId = upsertEvent(clusterKey, members, now);

            jdbc.update("DELETE FROM event_articles WHERE event_id = ?", eventId);
            for (ArticleText article : members) {
                jdbc.update("""
                        INSERT INTO event_articles (id, event_id, article_id, relevance_score, detected_at, created_at)
                        VALUES (?, ?, ?, NULL, ?, ?)
                        """, stableId(eventId + ":" + article.id()), eventId, article.id(),
                        Timestamp.from(article.publishedAt()), Timestamp.from(now));
                linkedArticles++;
            }
        }

        deleteStaleGeneratedEvents(currentKeys);
        return new BackfillReport(articles.size(), clusters.size(), clusters.size(), linkedArticles);
    }

    @Transactional(readOnly = true)
    public EventFeedResponse events() {
        List<EventResponse> items = jdbc.query("""
                SELECT e.id, e.title, e.description, c.name AS category, e.status,
                       e.first_detected_at, e.last_updated_at,
                       COUNT(DISTINCT a.id) AS article_count,
                       COUNT(DISTINCT a.source_id) AS source_count
                FROM events e
                JOIN event_articles ea ON ea.event_id = e.id
                JOIN articles a ON a.id = ea.article_id
                LEFT JOIN categories c ON c.id = a.category_id
                GROUP BY e.id, e.title, e.description, c.name
                HAVING COUNT(DISTINCT a.id) >= 2
                ORDER BY e.last_updated_at DESC, e.title ASC
                """, this::mapEventResponse);
        Map<UUID, List<EventPublisherResponse>> publishers = publishersFor(items.stream().map(EventResponse::id).toList());
        List<EventResponse> withPublishers = items.stream().map(item -> withSources(item, publishers)).toList();
        return new EventFeedResponse(withPublishers, withPublishers.size());
    }

    @Transactional(readOnly = true)
    public EventDetailResponse event(UUID id) {
        EventResponse summary = jdbc.query("""
                SELECT e.id, e.title, e.description, c.name AS category, e.status,
                       e.first_detected_at, e.last_updated_at,
                       COUNT(DISTINCT a.id) AS article_count,
                       COUNT(DISTINCT a.source_id) AS source_count
                FROM events e
                JOIN event_articles ea ON ea.event_id = e.id
                JOIN articles a ON a.id = ea.article_id
                LEFT JOIN categories c ON c.id = a.category_id
                WHERE e.id = ?
                GROUP BY e.id, e.title, e.description, c.name
                HAVING COUNT(DISTINCT a.id) >= 2
                """, this::mapEventResponse, id).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Event was not found."));
        List<EventPublisherResponse> sources = publishersFor(List.of(id)).getOrDefault(id, List.of());
        List<EventArticleResponse> articles = jdbc.query("""
                SELECT a.id, a.title, a.description, s.name AS publisher, a.published_at, a.original_url
                FROM event_articles ea
                JOIN articles a ON a.id = ea.article_id
                JOIN sources s ON s.id = a.source_id
                WHERE ea.event_id = ?
                ORDER BY a.published_at ASC, a.id ASC
                """, (result, row) -> new EventArticleResponse(
                result.getObject("id", UUID.class), result.getString("title"), result.getString("description"),
                result.getString("publisher"), instant(result, "published_at"), result.getString("original_url")), id);
        return new EventDetailResponse(summary.id(), summary.title(), summary.description(), summary.category(),
                summary.status(), summary.firstReportedAt(), summary.latestReportedAt(), summary.articleCount(),
                summary.sourceCount(), sources, articles);
    }

    private List<ArticleText> loadArticles() {
        return jdbc.query("""
                SELECT a.id, a.title, a.description, c.name AS category, a.source_id,
                       s.name AS source_name, a.original_url, a.published_at
                FROM articles a
                JOIN sources s ON s.id = a.source_id
                LEFT JOIN categories c ON c.id = a.category_id
                ORDER BY a.published_at DESC, a.id ASC
                LIMIT ?
                """, (result, row) -> new ArticleText(
                result.getObject("id", UUID.class), result.getString("title"), result.getString("description"),
                result.getString("category"), result.getObject("source_id", UUID.class),
                result.getString("source_name"), result.getString("original_url"), instant(result, "published_at")),
                MAX_BACKFILL_ARTICLES);
    }

    private UUID upsertEvent(String clusterKey, List<ArticleText> articles, Instant now) {
        ArticleText first = articles.get(0);
        Instant latest = articles.get(articles.size() - 1).publishedAt();
        String title = truncate(first.title(), 300);
        String description = evidenceDescription(articles);
        String status = latest.isAfter(now.minus(BaselineEventClusterer.MAX_EVENT_SPAN)) ? "DEVELOPING" : "QUIET";
        List<UUID> ids = jdbc.query("SELECT id FROM events WHERE cluster_key = ?", (result, row) -> result.getObject("id", UUID.class), clusterKey);
        if (ids.isEmpty()) {
            UUID id = stableId(clusterKey);
            jdbc.update("""
                    INSERT INTO events (id, cluster_key, title, description, status,
                        first_detected_at, last_updated_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, clusterKey, title, description, status, Timestamp.from(first.publishedAt()),
                    Timestamp.from(latest), Timestamp.from(now), Timestamp.from(now));
            return id;
        }
        UUID id = ids.get(0);
        jdbc.update("""
                UPDATE events SET title = ?, description = ?, status = ?, first_detected_at = ?,
                    last_updated_at = ?, updated_at = ?
                WHERE id = ?
                """, title, description, status, Timestamp.from(first.publishedAt()), Timestamp.from(latest),
                Timestamp.from(now), id);
        return id;
    }

    private void deleteStaleGeneratedEvents(Set<String> currentKeys) {
        if (currentKeys.isEmpty()) {
            jdbc.update("DELETE FROM events WHERE cluster_key IS NOT NULL");
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(currentKeys.size(), "?"));
        jdbc.update("DELETE FROM events WHERE cluster_key IS NOT NULL AND cluster_key NOT IN (" + placeholders + ")",
                currentKeys.toArray());
    }

    private Map<UUID, List<EventPublisherResponse>> publishersFor(List<UUID> eventIds) {
        if (eventIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(eventIds.size(), "?"));
        Map<UUID, List<EventPublisherResponse>> publishers = new HashMap<>();
        jdbc.query("""
                SELECT ea.event_id, s.id AS source_id, s.name, COUNT(DISTINCT a.id) AS article_count
                FROM event_articles ea
                JOIN articles a ON a.id = ea.article_id
                JOIN sources s ON s.id = a.source_id
                WHERE ea.event_id IN (
                """ + placeholders + ") GROUP BY ea.event_id, s.id, s.name ORDER BY s.name", result -> {
            UUID eventId = result.getObject("event_id", UUID.class);
            publishers.computeIfAbsent(eventId, ignored -> new ArrayList<>())
                    .add(new EventPublisherResponse(result.getObject("source_id", UUID.class),
                            result.getString("name"), result.getInt("article_count")));
        }, eventIds.toArray());
        return publishers;
    }

    private EventResponse withSources(EventResponse event, Map<UUID, List<EventPublisherResponse>> publishers) {
        List<EventPublisherResponse> sources = publishers.getOrDefault(event.id(), List.of());
        return new EventResponse(event.id(), event.title(), event.description(), event.category(), event.status(),
                event.firstReportedAt(), event.latestReportedAt(), event.articleCount(), sources.size(), sources);
    }

    private EventResponse mapEventResponse(ResultSet result, int row) throws SQLException {
        return new EventResponse(result.getObject("id", UUID.class), result.getString("title"),
                result.getString("description"), result.getString("category"), result.getString("status"),
                instant(result, "first_detected_at"), instant(result, "last_updated_at"),
                result.getInt("article_count"), result.getInt("source_count"), List.of());
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String evidenceDescription(List<ArticleText> articles) {
        StringBuilder description = new StringBuilder();
        Set<UUID> includedSources = new HashSet<>();
        for (ArticleText article : articles) {
            if (!includedSources.add(article.sourceId())) continue;
            String evidence = article.description() == null || article.description().isBlank()
                    ? article.title() : article.description().trim();
            String line = article.sourceName() + ": " + evidence;
            if (description.length() > 0) description.append("\n\n");
            int remaining = MAX_DESCRIPTION_LENGTH - description.length();
            if (line.length() > remaining) line = line.substring(0, remaining);
            description.append(line);
            if (description.length() >= MAX_DESCRIPTION_LENGTH || includedSources.size() >= 4) break;
        }
        return description.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        int boundary = text.lastIndexOf(' ', maxLength - 1);
        return text.substring(0, boundary > maxLength / 2 ? boundary : maxLength);
    }

    private UUID stableId(String value) {
        return UUID.nameUUIDFromBytes(("pulseai:event:" + value).getBytes(StandardCharsets.UTF_8));
    }

    public record BackfillReport(int articlesEvaluated, int eventsGenerated,
            int multiArticleEvents, int articleRelationshipsCreated) {}
}
