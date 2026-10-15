package com.pulseai.service;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ArticleRetentionService {
    private final JdbcTemplate jdbc;
    private final int hours, maxArticles;
    public ArticleRetentionService(JdbcTemplate jdbc, @Value("${app.news.retention.hours:48}") int hours, @Value("${app.news.retention.max-articles:2000}") int maxArticles) {
        if (hours < 1 || hours > 168 || maxArticles < 1 || maxArticles > 100000) throw new IllegalStateException("Retention must be 1-168 hours, with 1-100000 cached articles.");
        this.jdbc=jdbc; this.hours=hours; this.maxArticles=maxArticles;
    }
    public Instant cutoff() { return Instant.now().minus(Duration.ofHours(hours)); }
    public int hours() { return hours; }
    public int maxArticles() { return maxArticles; }
    @Scheduled(fixedDelayString="${app.news.retention.cleanup-ms:900000}", initialDelayString="${app.news.retention.cleanup-ms:900000}")
    @Transactional public int cleanup() {
        int deleted=jdbc.update("DELETE FROM articles WHERE published_at < ?", Timestamp.from(cutoff()));
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM articles", Long.class);
        int excess=(int)Math.max(0, count-maxArticles);
        if(excess>0) {
            var ids=jdbc.query("SELECT id FROM articles ORDER BY published_at ASC, id ASC LIMIT ?", (rs,n)->rs.getObject(1,UUID.class), excess);
            for(var id:ids) deleted+=jdbc.update("DELETE FROM articles WHERE id=?", id);
        }
        jdbc.update("DELETE FROM browser_sessions WHERE expires_at < ?", Timestamp.from(Instant.now()));
        jdbc.update("DELETE FROM reading_history WHERE opened_at < ?", Timestamp.from(Instant.now().minus(Duration.ofDays(30))));
        return deleted;
    }
}
