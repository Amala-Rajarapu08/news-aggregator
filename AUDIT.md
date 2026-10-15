# PulseAI integration audit - 2026-10-12

The repository was inspected before integration: active React routes and services, Spring controllers/security/JPA, Flyway V1-V3, ingestion adapters, FastAPI, tests, Compose, and documentation.

| Symptom | Root cause and correction |
|---|---|
| Articles/categories/sources returned 404 | Matching Spring controllers already existed under `/api/v1`. Frontend defaulted to relative `/api/v1`, but Vite had no API proxy and Nginx no equivalent forwarding. Added both; corrected stale absolute browser defaults. A backend not running now remains a visible connection error. |
| Backend could not start through Compose | Compose omitted the required JWT signing secret and news configuration. Added validated server environment wiring. |
| Guest/Preview workspace on entry | Active auth pages were presentation only; no identity provider, restoration, or route guard. Connected real signup/login, protected workspace routes, refresh cookies, current identity, and logout. |
| Saved/profile/preferences were placeholders | Working backend APIs were not wired to active pages; saves used local storage. Added server-backed providers and forms. Also corrected a legacy `userService.js` shadowing the typed real API service at bundle resolution. |
| Potential PostgreSQL filtered-query failures | Nullable JPQL parameters could produce PostgreSQL type inference problems. Replaced active query path with optional Criteria predicates. |
| No bounded catalog or accurate freshness | Existing ingestion had URL deduplication but no cache retention, saved-reference survival, or persisted status. Added V4, scheduled cleanup, row cap, citation backfill, and status. |
| Weak image hierarchy and inactive assistant | Added feed image URL extraction, safe image fallbacks, news-led dashboard, floating context-aware Copilot, and Pulse Lens. |
| Fake/pending intelligence | Active routes now use actual category/preferences/opening signals and catalog excerpts. ML/RAG/event features display explicit pending states. Dormant legacy fixture modules are preserved but excluded from the active production route graph. |

At this initial audit checkpoint, local PostgreSQL services were reachable, but credentials were unavailable and there was no `.env`. Existing `pulseai` access remained a manual setup requirement at that point. Actual integration was verified using PostgreSQL 18.6 in an isolated cluster: migrations, mappings, repositories, constraints, REST account workflows, retention, and live RSS.

No `.git` directory exists in this workspace, so branch/diff/commit status is unavailable. No migration checksum repair, applied migration edit, schema deletion in the existing database, or publishing was performed.

The [implementation report](docs/implementation-report.md) contains the final verification record and changed-file inventory. Earlier prototype-era documentation was updated to reflect current capabilities.

## Follow-up verification — 2026-10-12

After the ignored `.env` was supplied, the actual `pulseai` database was verified on PostgreSQL 18.6. V1–V4 are current; no tables were dropped or reset. The login 403 was traced to stale CORS startup settings and a stale Vite proxy target; the verified local path is Vite 5182 → Spring Boot 8080. Full backend suites, targeted auth suites, the live RSS smoke test, frontend build/tests, and the headless browser audit passed. See the implementation report for the exact results, remaining feature boundaries, and startup commands.
