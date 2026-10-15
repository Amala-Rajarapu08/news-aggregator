ALTER TABLE ingestion_status ADD COLUMN images_backfilled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ingestion_status ADD COLUMN feed_reports_json TEXT NOT NULL DEFAULT '[]';
