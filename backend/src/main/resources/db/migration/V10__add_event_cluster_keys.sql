ALTER TABLE events ADD COLUMN cluster_key VARCHAR(64);
CREATE UNIQUE INDEX uk_events_cluster_key ON events (cluster_key);
