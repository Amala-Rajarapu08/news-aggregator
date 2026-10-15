package com.pulseai.service;

import java.sql.Connection;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresVectorSchema {

    private static final Logger logger = LoggerFactory.getLogger(PostgresVectorSchema.class);
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private volatile boolean ready;

    public PostgresVectorSchema(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    public synchronized boolean ensureAvailable() {
        if (ready) return true;
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return false;
            Boolean available = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector')",
                    Boolean.class);
            if (!Boolean.TRUE.equals(available)) return false;

            Boolean installed = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                    Boolean.class);
            if (!Boolean.TRUE.equals(installed)) jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS article_embeddings (
                        article_id UUID PRIMARY KEY REFERENCES articles(id) ON DELETE CASCADE,
                        model_version VARCHAR(200) NOT NULL,
                        embedding VECTOR(384) NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_article_embeddings_model_version ON article_embeddings (model_version)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_article_embeddings_hnsw_cosine ON article_embeddings USING hnsw (embedding vector_cosine_ops)");
            ready = true;
            return true;
        } catch (Exception exception) {
            logger.warn("pgvector storage is unavailable; semantic search will use the labeled keyword fallback exceptionType={}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }
}
