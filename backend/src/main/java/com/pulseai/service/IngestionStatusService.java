package com.pulseai.service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseai.ingestion.IngestionReport;
import com.pulseai.ingestion.FeedIngestionReport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
@Service
public class IngestionStatusService {
    private static final Logger logger = LoggerFactory.getLogger(IngestionStatusService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private volatile Boolean diagnosticsSchemaAvailable;
    private volatile List<FeedIngestionReport> latestFeedReports = List.of();
    private volatile int latestImagesBackfilled;
    public IngestionStatusService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }
    public void record(IngestionReport report) {
        boolean successfulProviderFetch = report.providers().stream()
                .anyMatch(provider -> provider.failures() == 0 || provider.fetched() > 0);
        boolean successfulFeedFetch = report.feedReports().stream()
                .anyMatch(feed -> feed.lastSuccessfulAt() != null);
        boolean success = successfulProviderFetch || successfulFeedFetch;
        try {
            if (hasDiagnosticsSchema()) {
                String feedReports = objectMapper.writeValueAsString(report.feedReports());
                jdbc.update("UPDATE ingestion_status SET last_attempt_at=?, last_success_at=CASE WHEN ? THEN ? ELSE last_success_at END, fetched=?, inserted=?, duplicates_skipped=?, invalid_skipped=?, failures=?, providers=?, images_backfilled=?, feed_reports_json=? WHERE id=1",
                        Timestamp.from(report.completedAt()), success, Timestamp.from(report.completedAt()), report.fetched(),
                        report.inserted(), report.duplicatesSkipped(), report.invalidSkipped(), report.failures(),
                        report.providers().size(), report.imagesBackfilled(), feedReports);
            } else {
                jdbc.update("UPDATE ingestion_status SET last_attempt_at=?, last_success_at=CASE WHEN ? THEN ? ELSE last_success_at END, fetched=?, inserted=?, duplicates_skipped=?, invalid_skipped=?, failures=?, providers=? WHERE id=1",
                        Timestamp.from(report.completedAt()), success, Timestamp.from(report.completedAt()), report.fetched(),
                        report.inserted(), report.duplicatesSkipped(), report.invalidSkipped(), report.failures(),
                        report.providers().size());
            }
            latestFeedReports = report.feedReports();
            latestImagesBackfilled = report.imagesBackfilled();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not serialize ingestion diagnostics.", exception);
        }
    }
    public Status status(boolean scheduled, long interval, ArticleRetentionService retention, java.util.List<String> providers) {
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM ingestion_status WHERE id=1");
        boolean hasPersistentDiagnostics = hasDiagnosticsSchema();
        List<FeedIngestionReport> feedReports = hasPersistentDiagnostics
                ? readFeedReports(row.get("feed_reports_json")) : latestFeedReports;
        return new Status(
            instant(row.get("last_attempt_at")), instant(row.get("last_success_at")),
            integer(row, "fetched"), integer(row, "inserted"), integer(row, "duplicates_skipped"),
            integer(row, "invalid_skipped"), integer(row, "failures"),
            hasPersistentDiagnostics ? integer(row, "images_backfilled") : latestImagesBackfilled, scheduled, interval,
            retention.hours(), retention.maxArticles(), jdbc.queryForObject("SELECT COUNT(*) FROM articles", Long.class),
            providers, feedReports, categoryCounts(), sourceCounts(),
            jdbc.queryForObject("SELECT COUNT(*) FROM articles WHERE image_url IS NOT NULL", Long.class));
    }
    private boolean hasDiagnosticsSchema() {
        Boolean available = diagnosticsSchemaAvailable;
        if (available != null) return available;
        synchronized (this) {
            if (diagnosticsSchemaAvailable != null) return diagnosticsSchemaAvailable;
            try {
                jdbc.queryForObject("SELECT images_backfilled, feed_reports_json FROM ingestion_status WHERE id=1", (rs, row) -> true);
                diagnosticsSchemaAvailable = true;
            } catch (DataAccessException exception) {
                diagnosticsSchemaAvailable = false;
            }
            return diagnosticsSchemaAvailable;
        }
    }
    private List<FeedIngestionReport> readFeedReports(Object value) {
        if (value == null) return List.of();
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<>() {});
        } catch (java.io.IOException exception) {
            logger.warn("Persisted ingestion diagnostics could not be parsed exceptionType={}", exception.getClass().getSimpleName());
            return List.of();
        }
    }
    private Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported ingestion timestamp type: " + value.getClass().getSimpleName());
    }
    private int integer(Map<String, Object> row, String column) { return ((Number) row.get(column)).intValue(); }
    private Map<String, Long> categoryCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT COALESCE(c.slug, 'uncategorized'), COUNT(*) FROM articles a LEFT JOIN categories c ON c.id=a.category_id GROUP BY c.slug ORDER BY c.slug",
                (RowCallbackHandler) rs -> counts.put(rs.getString(1), rs.getLong(2)));
        return Map.copyOf(counts);
    }
    private Map<String, Long> sourceCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT s.name, COUNT(*) FROM articles a JOIN sources s ON s.id=a.source_id GROUP BY s.name ORDER BY s.name",
                (RowCallbackHandler) rs -> counts.put(rs.getString(1), rs.getLong(2)));
        return Map.copyOf(counts);
    }
    public record Status(Instant lastAttemptAt, Instant lastSuccessfulRefreshAt, int fetched, int inserted, int duplicatesSkipped, int invalidSkipped, int failures, int imagesBackfilled,
        boolean scheduled, long refreshIntervalMs, int retentionHours, int maxCachedArticles, long cachedArticles,
        List<String> configuredProviders, List<FeedIngestionReport> feeds,
        Map<String, Long> categoryDistribution, Map<String, Long> publisherDistribution, long articlesWithImages) {}
}
