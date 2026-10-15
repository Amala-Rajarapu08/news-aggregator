INSERT INTO categories (id, name, slug, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000309', 'General', 'general', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (slug) DO NOTHING;
