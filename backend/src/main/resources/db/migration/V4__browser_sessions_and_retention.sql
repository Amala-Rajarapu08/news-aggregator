CREATE TABLE browser_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_browser_sessions_expiry ON browser_sessions(expires_at);
CREATE INDEX idx_browser_sessions_user ON browser_sessions(user_id);
ALTER TABLE user_preferences ADD COLUMN history_enabled BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE user_preferred_categories (
    preference_id UUID NOT NULL REFERENCES user_preferences(id) ON DELETE CASCADE,
    category_slug VARCHAR(100) NOT NULL REFERENCES categories(slug),
    PRIMARY KEY (preference_id, category_slug)
);
-- Minimal citations survive expiry of the temporary catalog.
ALTER TABLE saved_articles ADD COLUMN article_key UUID;
ALTER TABLE saved_articles ADD COLUMN title VARCHAR(500);
ALTER TABLE saved_articles ADD COLUMN canonical_url VARCHAR(2048);
ALTER TABLE saved_articles ADD COLUMN publisher VARCHAR(120);
ALTER TABLE saved_articles ADD COLUMN publisher_domain VARCHAR(253);
ALTER TABLE saved_articles ADD COLUMN publisher_url VARCHAR(2048);
ALTER TABLE saved_articles ADD COLUMN publisher_id UUID;
ALTER TABLE saved_articles ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE saved_articles ADD COLUMN category VARCHAR(80);
UPDATE saved_articles SET
    article_key = article_id,
    title = (SELECT title FROM articles WHERE id = article_id),
    canonical_url = (SELECT canonical_url FROM articles WHERE id = article_id),
    publisher = (SELECT s.name FROM articles a JOIN sources s ON s.id = a.source_id WHERE a.id = article_id),
    publisher_domain = (SELECT s.domain FROM articles a JOIN sources s ON s.id = a.source_id WHERE a.id = article_id),
    publisher_url = (SELECT s.base_url FROM articles a JOIN sources s ON s.id = a.source_id WHERE a.id = article_id),
    publisher_id = (SELECT source_id FROM articles WHERE id = article_id),
    published_at = (SELECT published_at FROM articles WHERE id = article_id),
    category = (SELECT c.name FROM articles a JOIN categories c ON c.id = a.category_id WHERE a.id = article_id);
ALTER TABLE saved_articles ALTER COLUMN article_key SET NOT NULL;
ALTER TABLE saved_articles ALTER COLUMN title SET NOT NULL;
ALTER TABLE saved_articles ALTER COLUMN canonical_url SET NOT NULL;
ALTER TABLE saved_articles ALTER COLUMN publisher SET NOT NULL;
ALTER TABLE saved_articles ALTER COLUMN publisher_domain SET NOT NULL;
ALTER TABLE saved_articles ALTER COLUMN publisher_url SET NOT NULL;
ALTER TABLE saved_articles ALTER COLUMN publisher_id SET NOT NULL;
ALTER TABLE saved_articles ALTER COLUMN published_at SET NOT NULL;
ALTER TABLE saved_articles ALTER COLUMN article_id DROP NOT NULL;
ALTER TABLE saved_articles DROP CONSTRAINT fk_saved_articles_article;
ALTER TABLE saved_articles ADD CONSTRAINT fk_saved_articles_article FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE SET NULL;
ALTER TABLE saved_articles ADD CONSTRAINT uk_saved_articles_user_key UNIQUE (user_id, article_key);
CREATE TABLE ingestion_status (
    id INTEGER PRIMARY KEY,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    fetched INTEGER NOT NULL DEFAULT 0,
    inserted INTEGER NOT NULL DEFAULT 0,
    duplicates_skipped INTEGER NOT NULL DEFAULT 0,
    invalid_skipped INTEGER NOT NULL DEFAULT 0,
    failures INTEGER NOT NULL DEFAULT 0,
    providers INTEGER NOT NULL DEFAULT 0
);
INSERT INTO ingestion_status (id) VALUES (1);
CREATE INDEX idx_articles_created_at ON articles(created_at);
