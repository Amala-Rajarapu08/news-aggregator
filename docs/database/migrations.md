# Migration ownership and retention

Versioned SQL lives in `backend/src/main/resources/db/migration`. Flyway runs at startup; JPA uses `ddl-auto=validate`. Never modify V1, V2, V3, or any migration after application to a database. Add a forward correction instead.

| Migration | Purpose |
|---|---|
| V1 | Immutable `SELECT 1` foundation. |
| V2 | Normalized users/preferences, publisher/category/topic catalog, articles, saved/history, events, notifications; constraints and indexes. |
| V3 | Supported category taxonomy, not fabricated news. |
| V4 | Hashed browser sessions; explicit category preferences/history opt-in; saved citation backfill; nullable article FK ON DELETE SET NULL; single-row ingestion status; cleanup index. |

Before applying to an existing database, verify its credentials, migration privileges, current Flyway history/checksums, and backup. Do not use Hibernate update/create, Flyway clean, or checksum repair to mask a failed upgrade.

`FlywayUpgradeTest` migrates a private test schema to V3, inserts an existing saved reference, applies V4, expires the article, and checks that citation metadata survives. It runs in the isolated real PostgreSQL verification script. Workspace tests additionally exercise row caps and archived API responses. Docker's PostgreSQL 17 constraint test is optional when Docker is available.

Default article retention is 48h, max 2,000 rows, cleanup every 15 minutes and during ingestion. The cap can expire newer rows before the age limit. Saved citations are limited to 500/account and retained until unsaved or account deletion; no excerpt/image is preserved after expiry. Reading openings expire with their article or after 30 days. Expired browser sessions are removed. PostgreSQL physical disk space is reclaimed through normal vacuum/reuse behavior, not per-row file shrinkage.
