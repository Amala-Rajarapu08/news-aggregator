# Database

PostgreSQL is the active system of record. Flyway owns schema changes; Hibernate validates mappings. V1 foundation, V2 domain, V3 category taxonomy, and V4 browser sessions/retention form the migration chain. Applied migrations must remain immutable.

V4 backfills minimal saved citations and uses ON DELETE SET NULL so cache expiry preserves user references. Article metadata has publication-based retention and a count cap; history is optional and bounded; refresh tokens are hashed. No full article bodies, downloaded image binaries, permanent queries/responses, embeddings, or vectors are stored by the current code.

Use your existing `pulseai` database through the ignored root `.env` and `scripts/Start-Backend.ps1`. Actual integration tests can run without Docker through `scripts/Verify-Postgres.ps1`, which creates and stops an isolated PostgreSQL cluster. It also tests V3-to-V4 upgrades with an existing saved record. See [migration ownership](../docs/database/migrations.md).

Compose provides an alternative PostgreSQL 17 instance and persistent named volume. Its pgvector-capable image does not enable the vector extension; that remains a future migration after model selection.
