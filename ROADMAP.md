# PulseAI implementation roadmap

## Current integration checkpoint — 2026-10-13

- Existing PostgreSQL 18 database is at Flyway V6 and retains one user and one saved citation. No data was reset. The current catalog has 454 articles, 187 sources, and 367 article image URLs. Counts by category are documented in the [implementation report](docs/implementation-report.md).
- All eight RSS feeds returned HTTP 200. NewsAPI returned HTTP 200 and is contributing categorized articles. GNews is configured but returned HTTP 403; check quota/subscription state. World remains empty.
- Original article links are preserved separately from canonical deduplication URLs. Provider image extraction and the editorial image fallback are covered by tests; one persisted image returned HTTP 200 with `image/jpeg`.
- FastAPI serves real normalized 384-dimensional `all-MiniLM-L6-v2` vectors. The backend has bounded article backfill and pgvector cosine search code. The local PostgreSQL 18 installation has no vector extension, so the database has 0 embeddings and semantic search reports `keyword-fallback` with `pgvector-unavailable`.
- Latest verification: backend H2 55 tests (0 failures, 3 skipped), isolated PostgreSQL 55 tests (0 failures, 2 skipped), frontend 28 tests, frontend production build/typecheck, and AI service 3 tests. A real embedding request returned HTTP 200 with unit norm.
- See the [news ingestion guide](docs/architecture/news-ingestion.md) and [implementation report](docs/implementation-report.md) for current counts, API results, provider terms, and limitations.

> The checkpoints below are historical; their counts and configuration are superseded by the current section above.

## Continuation checkpoint — 2026-10-12

- Added independent regression coverage for RSS articles with no publisher image and for the frontend publisher fallback.
- Added the missing root `.env.example`; the ignored local `.env` now tags the NASA RSS starter feed as `science|...` and enables scheduled refresh alongside the database settings.
- Connected only to the existing `pulseai` database. Initial read-only inspection found an empty public schema; Spring Boot validated and applied V1–V4, and later startup confirmed schema version 4 current. No tables or data were reset or dropped.
- PostgreSQL-backed auth smoke flow passed; its temporary account was deleted. The live NASA feed inserted 3 recent articles, and the API proxy served those same rows through Vite.
- Verification in this continuation: backend H2 and isolated PostgreSQL suites each ran 42 tests with no failures; targeted auth suites passed 7/7 on both databases; frontend 19 tests and production build passed; AI health test passed; live RSS and browser E2E passed. See [implementation report](docs/implementation-report.md).
- The app is running locally at `http://127.0.0.1:5182` with Spring Boot on port 8080. The Vite proxy is explicitly pointed at 8080. Flyway warned that PostgreSQL 18.6 is newer than its latest tested PostgreSQL 17 version.
- The auth 403 came from stale backend CORS startup config and a stale Vite proxy target. The local `.env` and local-profile fallback allow exact `localhost`/`127.0.0.1` origins on ports 5180–5182; both services were restarted and the proxied anonymous refresh now returns the expected 401.

## Completed integration boundary

- Same-origin API routing; persisted, filtered, paginated catalog; real PostgreSQL verification.
- Actual account registration/login, refresh restoration, protected routes, logout and bearer revocation.
- RSS/Atom, GNews, and NewsAPI ingestion, provider bounds/cooldown, canonical deduplication, actual refresh status.
- FastAPI embedding endpoint and backend pgvector indexing/search code, with explicit keyword fallback while the local extension is absent.
- Configurable article retention/count cap; minimal saved citations survive expiry.
- Real Discover/category/source/detail screens, bookmarks, profile/preferences, optional reading history and clearing.
- Responsive editorial dashboard, publisher image URLs/fallbacks, floating Copilot, and Pulse Lens.
- Explicit category recommendations, consented News DNA/analytics, on-demand digest, and observed coverage trends.
- Backend/frontend/AI tests, live RSS verification, and desktop/mobile browser checks.

The local PostgreSQL credentials are set in ignored `.env`; rotate them before production use. The local configuration combines eight RSS feeds with NewsAPI and GNews. NewsAPI returned HTTP 200; GNews returned HTTP 403, so check its quota/subscription status. No World articles are currently stored, and image reuse still requires checking publisher rights. Docker/Compose runtime requires Docker and available host ports.

## Next: grounded intelligence

1. Install pgvector built for local PostgreSQL 18 or point a separate development database at a compatible pgvector build. Do not reset or redirect the existing database without an explicit backup and plan.
2. Verify automatic embedding-table creation, bounded backfill, and semantic ranking after the extension is available. Add relevance checks for known query/article pairs; report measured results only.
3. Select licensed permitted text and evaluation data for versioned classification/entity extraction. Keep the default metadata-only, bounded storage contract.
4. Evaluate full-text/hybrid retrieval with source/time filtering and quality tests; keyword search remains the explicit baseline.
5. Add an LLM/RAG assistant adapter behind the existing Copilot contract: evidence-bound claims, citations, abstention, injection defenses, deadline/cost limits, and privacy consent. Preserve unavailable states on insufficient evidence.
6. Evaluate event grouping and membership before adding timelines, perspectives, developing-story claims, or comparisons.
7. Add measured recommendation ranking/diversity only after sufficient consented signals and time-split evaluation.

Automatic briefing delivery, notifications, password reset/email verification, email changes, account export/deletion, and verified event/topic extraction remain pending. Editorial topic following works when a trusted operator has populated topic rows; no topics are fabricated.

## Production preparation

Add authentication/search abuse throttling, HTTPS and production cookie configuration, managed signing keys/audience policy, dependency/container scanning, CI, PostgreSQL backups and restore tests, safe distributed ingestion coordination, durable provider quota accounting across restarts, and reviewed account deletion/retention. A single-instance scheduler and process-local provider cooldown are the current scope.

## Precise continuation prompt

"Continue the existing PulseAI implementation documented in docs/implementation-report.md. Preserve the API-backed account/catalog workflows, immutable V1-V4 migrations, forward-only V5-V6 schema, bounded retention, honest intelligence labels, and editorial identity. The existing local pulseai database is on schema version 6 with existing user and saved-citation data; inspect before any further migration and never edit applied migrations. NewsAPI and eight RSS feeds are active; GNews returned HTTP 403 and World currently has no articles. The FastAPI embedding model and backend pgvector search code are implemented, but this PostgreSQL 18 installation lacks pgvector, so do not claim vector search is active. Install a compatible extension without resetting the database, then verify backfill and relevance. Add sources only after checking their feed and media rights. Preserve citations, abstention, privacy controls, and honest fallback labels. Run backend, frontend, AI, and real PostgreSQL tests; report only measured capabilities. Do not fabricate summaries, events, comparisons, metrics, or demo fallback news."
