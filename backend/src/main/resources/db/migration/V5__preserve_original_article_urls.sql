ALTER TABLE articles ADD COLUMN original_url VARCHAR(2048);

UPDATE articles
SET original_url = canonical_url;

ALTER TABLE articles ALTER COLUMN original_url SET NOT NULL;
ALTER TABLE articles ADD CONSTRAINT ck_articles_original_url
    CHECK (original_url LIKE 'http://%' OR original_url LIKE 'https://%');

-- Preserve the source link in retained saved citations too.
UPDATE saved_articles
SET canonical_url = (
    SELECT article.original_url
    FROM articles article
    WHERE article.id = saved_articles.article_id
)
WHERE article_id IS NOT NULL;
