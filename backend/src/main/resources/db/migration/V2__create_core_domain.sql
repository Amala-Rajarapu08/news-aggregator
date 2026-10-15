CREATE TABLE users (
    id UUID PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = LOWER(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE sources (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    domain VARCHAR(253) NOT NULL,
    base_url VARCHAR(2048) NOT NULL,
    logo_url VARCHAR(2048),
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_sources_domain UNIQUE (domain),
    CONSTRAINT ck_sources_base_url CHECK (base_url LIKE 'http://%' OR base_url LIKE 'https://%'),
    CONSTRAINT ck_sources_logo_url CHECK (logo_url IS NULL OR logo_url LIKE 'http://%' OR logo_url LIKE 'https://%'),
    CONSTRAINT ck_sources_domain_normalized CHECK (domain = LOWER(domain))
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
);

CREATE TABLE topics (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL,
    description VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_topics_slug UNIQUE (slug)
);

CREATE TABLE articles (
    id UUID PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description VARCHAR(2000),
    canonical_url VARCHAR(2048) NOT NULL,
    image_url VARCHAR(2048),
    author VARCHAR(200),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    language VARCHAR(12) NOT NULL,
    source_id UUID NOT NULL,
    category_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_articles_canonical_url UNIQUE (canonical_url),
    CONSTRAINT ck_articles_canonical_url CHECK (canonical_url LIKE 'http://%' OR canonical_url LIKE 'https://%'),
    CONSTRAINT ck_articles_image_url CHECK (image_url IS NULL OR image_url LIKE 'http://%' OR image_url LIKE 'https://%'),
    CONSTRAINT fk_articles_source FOREIGN KEY (source_id) REFERENCES sources(id),
    CONSTRAINT fk_articles_category FOREIGN KEY (category_id) REFERENCES categories(id)
);
CREATE INDEX idx_articles_published_at ON articles(published_at DESC);
CREATE INDEX idx_articles_source_published_at ON articles(source_id, published_at DESC);
CREATE INDEX idx_articles_category_published_at ON articles(category_id, published_at DESC);

CREATE TABLE article_topics (
    id UUID PRIMARY KEY,
    article_id UUID NOT NULL,
    topic_id UUID NOT NULL,
    relevance_score DOUBLE PRECISION,
    extraction_source VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_article_topics_article_topic UNIQUE (article_id, topic_id),
    CONSTRAINT ck_article_topics_relevance CHECK (relevance_score IS NULL OR (relevance_score >= 0 AND relevance_score <= 1)),
    CONSTRAINT ck_article_topics_source CHECK (extraction_source IN ('EDITORIAL', 'NLP', 'EXPLICIT', 'READING_ACTIVITY')),
    CONSTRAINT fk_article_topics_article FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE,
    CONSTRAINT fk_article_topics_topic FOREIGN KEY (topic_id) REFERENCES topics(id)
);
CREATE INDEX idx_article_topics_topic_article ON article_topics(topic_id, article_id);

CREATE TABLE saved_articles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    article_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_saved_articles_user_article UNIQUE (user_id, article_id),
    CONSTRAINT fk_saved_articles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_articles_article FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);
CREATE INDEX idx_saved_articles_user_created ON saved_articles(user_id, created_at DESC);
CREATE INDEX idx_saved_articles_article ON saved_articles(article_id);

CREATE TABLE reading_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    article_id UUID NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    scroll_percentage INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_reading_history_duration CHECK (duration_seconds >= 0),
    CONSTRAINT ck_reading_history_scroll CHECK (scroll_percentage IS NULL OR (scroll_percentage >= 0 AND scroll_percentage <= 100)),
    CONSTRAINT fk_reading_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reading_history_article FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);
CREATE INDEX idx_reading_history_user_opened ON reading_history(user_id, opened_at DESC);
CREATE INDEX idx_reading_history_article ON reading_history(article_id);

CREATE TABLE user_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    briefing_frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    recommendations_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_preferences_user UNIQUE (user_id),
    CONSTRAINT ck_user_preferences_frequency CHECK (briefing_frequency IN ('DAILY', 'WEEKLY', 'OFF')),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE user_topic_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    topic_id UUID NOT NULL,
    preference_source VARCHAR(24) NOT NULL DEFAULT 'EXPLICIT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_topic_preferences_user_topic UNIQUE (user_id, topic_id),
    CONSTRAINT ck_user_topic_preferences_source CHECK (preference_source IN ('EDITORIAL', 'NLP', 'EXPLICIT', 'READING_ACTIVITY')),
    CONSTRAINT fk_user_topic_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_topic_preferences_topic FOREIGN KEY (topic_id) REFERENCES topics(id)
);
CREATE INDEX idx_user_topic_preferences_topic ON user_topic_preferences(topic_id, user_id);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(16) NOT NULL DEFAULT 'DEVELOPING',
    first_detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_events_status CHECK (status IN ('DEVELOPING', 'QUIET', 'CLOSED'))
);
CREATE INDEX idx_events_last_updated ON events(last_updated_at DESC);

CREATE TABLE event_articles (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    article_id UUID NOT NULL,
    relevance_score DOUBLE PRECISION,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_event_articles_event_article UNIQUE (event_id, article_id),
    CONSTRAINT ck_event_articles_relevance CHECK (relevance_score IS NULL OR (relevance_score >= 0 AND relevance_score <= 1)),
    CONSTRAINT fk_event_articles_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_articles_article FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);
CREATE INDEX idx_event_articles_article ON event_articles(article_id, event_id);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(24) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_notifications_type CHECK (type IN ('EVENT', 'BRIEFING', 'TOPIC', 'RECOMMENDATION', 'SYSTEM')),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
