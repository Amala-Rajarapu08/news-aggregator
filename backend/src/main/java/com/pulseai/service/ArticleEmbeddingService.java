package com.pulseai.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ArticleEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleEmbeddingService.class);
    private static final int BACKFILL_BATCH_SIZE = 20;

    private final JdbcTemplate jdbc;
    private final PostgresVectorSchema vectorSchema;
    private final AiEmbeddingClient embeddingClient;
    private volatile Instant retryAfter = Instant.EPOCH;

    public ArticleEmbeddingService(
            JdbcTemplate jdbc,
            PostgresVectorSchema vectorSchema,
            AiEmbeddingClient embeddingClient) {
        this.jdbc = jdbc;
        this.vectorSchema = vectorSchema;
        this.embeddingClient = embeddingClient;
    }

    public boolean isReady() {
        return vectorSchema.ensureAvailable();
    }

    public long indexedCount() {
        if (!isReady()) return 0;
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM article_embeddings WHERE model_version = ?",
                Long.class,
                embeddingClient.modelName());
        return count == null ? 0 : count;
    }

    @Scheduled(
            initialDelayString = "${app.ai.embedding-backfill-initial-delay-ms:20000}",
            fixedDelayString = "${app.ai.embedding-backfill-interval-ms:60000}")
    public void backfillMissingArticles() {
        if (Instant.now().isBefore(retryAfter) || !isReady()) return;
        try {
            indexMissingBatch(BACKFILL_BATCH_SIZE);
        } catch (RuntimeException exception) {
            retryAfter = Instant.now().plusSeconds(60);
            logger.warn("Article embedding batch failed exceptionType={}", exception.getClass().getSimpleName());
        }
    }

    int indexMissingBatch(int limit) {
        List<PendingArticle> pending = jdbc.query("""
                SELECT a.id, a.title, a.description
                FROM articles a
                LEFT JOIN article_embeddings e ON e.article_id = a.id
                WHERE e.article_id IS NULL OR e.model_version <> ?
                ORDER BY a.published_at DESC, a.id
                LIMIT ?
                """, (result, row) -> new PendingArticle(
                        result.getObject("id", UUID.class),
                        result.getString("title"),
                        result.getString("description")),
                embeddingClient.modelName(), limit);
        if (pending.isEmpty()) return 0;

        var response = embeddingClient.embed(pending.stream().map(PendingArticle::text).toList());
        for (int index = 0; index < pending.size(); index++) {
            jdbc.update("""
                    INSERT INTO article_embeddings (article_id, model_version, embedding, updated_at)
                    VALUES (?, ?, ?::vector, CURRENT_TIMESTAMP)
                    ON CONFLICT (article_id) DO UPDATE SET
                        model_version = EXCLUDED.model_version,
                        embedding = EXCLUDED.embedding,
                        updated_at = CURRENT_TIMESTAMP
                    """, pending.get(index).id(), response.model(), vectorLiteral(response.embeddings().get(index)));
        }
        retryAfter = Instant.EPOCH;
        logger.info("Article embedding batch indexed count={} model={}", pending.size(), response.model());
        return pending.size();
    }

    private String vectorLiteral(List<Double> vector) {
        return "[" + String.join(",", vector.stream().map(value -> Double.toString(value)).toList()) + "]";
    }

    private record PendingArticle(UUID id, String title, String description) {
        private String text() {
            return title + (description == null || description.isBlank() ? "" : "\n" + description);
        }
    }
}
