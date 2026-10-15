package com.pulseai.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PgVectorSearchRepository {

    private final JdbcTemplate jdbc;

    public PgVectorSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Hit> search(
            String vector,
            String model,
            String category,
            UUID sourceId,
            Instant from,
            Instant to,
            int page,
            int size) {
        List<Object> args = new ArrayList<>();
        args.add(vector);
        String filters = filters(model, category, sourceId, from, to, args);
        args.add(vector);
        args.add(size);
        args.add(Math.multiplyExact(page, size));
        return jdbc.query("""
                SELECT a.id, 1 - (e.embedding <=> CAST(? AS vector)) AS relevance
                FROM article_embeddings e
                JOIN articles a ON a.id = e.article_id
                LEFT JOIN categories c ON c.id = a.category_id
                """ + filters + " ORDER BY e.embedding <=> CAST(? AS vector) LIMIT ? OFFSET ?",
                (result, row) -> new Hit(result.getObject("id", UUID.class), result.getDouble("relevance")),
                args.toArray());
    }

    public long count(String model, String category, UUID sourceId, Instant from, Instant to) {
        List<Object> args = new ArrayList<>();
        String filters = filters(model, category, sourceId, from, to, args);
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM article_embeddings e
                JOIN articles a ON a.id = e.article_id
                LEFT JOIN categories c ON c.id = a.category_id
                """ + filters, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private String filters(String model, String category, UUID sourceId, Instant from, Instant to, List<Object> args) {
        StringBuilder sql = new StringBuilder(" WHERE e.model_version = ?");
        args.add(model);
        if (category != null && !category.isBlank()) {
            sql.append(" AND (LOWER(c.slug) = LOWER(?) OR LOWER(c.name) = LOWER(?))");
            args.add(category);
            args.add(category);
        }
        if (sourceId != null) {
            sql.append(" AND a.source_id = ?");
            args.add(sourceId);
        }
        if (from != null) {
            sql.append(" AND a.published_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND a.published_at <= ?");
            args.add(Timestamp.from(to));
        }
        return sql.toString();
    }

    public record Hit(UUID articleId, double relevance) {}
}
