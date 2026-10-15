-- Keep the existing application usable on PostgreSQL installations that have
-- not installed pgvector yet. PostgresVectorSchema completes this idempotently
-- after the extension becomes available and the database role permits it.
DO $vector$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        EXECUTE 'CREATE TABLE IF NOT EXISTS article_embeddings (
            article_id UUID PRIMARY KEY REFERENCES articles(id) ON DELETE CASCADE,
            model_version VARCHAR(200) NOT NULL,
            embedding VECTOR(384) NOT NULL,
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
        )';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_article_embeddings_model_version
            ON article_embeddings (model_version)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_article_embeddings_hnsw_cosine
            ON article_embeddings USING hnsw (embedding vector_cosine_ops)';
    ELSE
        RAISE NOTICE 'pgvector is not installed; semantic search remains in keyword fallback mode';
    END IF;
END
$vector$;
