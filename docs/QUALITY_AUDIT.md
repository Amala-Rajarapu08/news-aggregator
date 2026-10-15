# PulseAI quality audit

**Checked:** 2026-10-13. Live database checks were read-only. The backend currently listening on port 8080 was started with the audit-only mode, which disables scheduled ingestion, retention cleanup, and embedding backfill.

## Findings and repairs

### Publisher discovery

The frontend calls `GET /api/v1/sources/available` with optional `category` and `q` parameters. An older process on port 8080 did not have that route, so Spring treated `available` as the `{id}` path variable and returned HTTP 400. The launcher now checks this contract before reusing a backend and refuses to stop or replace unrelated processes.

The current route also had two PostgreSQL query failures. Optional `null` parameters reached `lower()` as `bytea` (`SQLSTATE 42883`), and PostgreSQL rejected `SELECT DISTINCT source ORDER BY lower(source.name)` (`SQLSTATE 42P10`). `ArticleCatalogService` now normalizes absent filters to empty strings, and the repository sorts by the selected source name. Empty matches return HTTP 200 with an empty array.

The Discover regression tests cover All and all eight category tabs, URL filters, publisher search, category changes, retry, and a genuine empty publisher list. Live PostgreSQL calls now return HTTP 200 for All and every category. Publisher search and article `sourceId` filtering also work.

### Ingestion and categories

Provider HTTP requests now have bounded connect/request/deadline limits and a 5 MiB response cap. The client retries one time for transport failures, timeouts, and selected 5xx responses; it cools down rate-limited hosts and GNews after HTTP 403. Provider logs report status and a finite reason without printing request URLs or API keys. `CategoryMapper` no longer maps `nation` or `national` to World; only explicit `world` and `international` labels map there.

The latest detailed provider snapshot already recorded in [implementation-report.md](implementation-report.md) was at `2026-10-13T08:28:39Z`: all eight configured RSS feeds returned HTTP 200 (NASA 10, SEC 25, NIST technology 40, NIST health 40, CISA 10, PBS 20, ESPN 36, TechCrunch 20). A NewsAPI business probe returned HTTP 200. GNews returned HTTP 403; [GNews documents 403 as a daily quota limit or expired subscription](https://docs.gnews.io/error-handling). [NewsAPI documents 401 for missing/invalid keys and 429 for rate limiting](https://newsapi.org/docs/errors).

The configured NewsAPI categories are business, entertainment, health, science, sports, and technology. GNews is configured for world, nation, business, technology, entertainment, sports, science, and health. The `nation` provider category is deliberately not mapped to World.

The most recent persisted aggregate attempt is `2026-10-13T13:20:13Z`: 0 fetched, 0 inserted, and 22 failures; the last successful attempt is `2026-10-13T04:57:13Z`. Per-feed reports are held in process memory, so the restarted backend returns no per-feed detail for that prior attempt. Direct external provider verification was unavailable in this audit environment, so the 13:20 attempt’s per-provider causes remain unverified. The running audit instance reports `scheduled=false` and makes no automatic provider requests.

The live API and PostgreSQL snapshot agree:

| Category | Articles | Publishers with matching articles |
|---|---:|---:|
| All | 453 | 187 |
| Business | 70 | 40 |
| Entertainment | 63 | 37 |
| Health | 54 | 33 |
| Politics | 20 | 1 |
| Science | 65 | 33 |
| Sports | 90 | 32 |
| Technology | 91 | 49 |
| World | 0 | 0 |

World is genuinely empty in the stored catalog. No articles were fabricated or relabeled to fill it. The previous detailed run’s GNews 403 and the latest aggregate failures mean the configured providers have not supplied verified fresh World coverage.

### Images and Discover filters

RSS parsing accepts publisher media tags, thumbnails, enclosures, relative URLs, and image URLs in excerpts. Provider image fields flow through normalization and persistence. The optional article-page metadata resolver runs during ingestion only when the provider supplied no image and rejects private hosts. The shared frontend image component uses safe HTTP(S) URLs, lazy loading, async decoding, and an accessible compact publisher-attributed fallback. Dashboard, Discover, recommendations, saved stories, and article detail share that component.

There are 366 stored image URLs for 453 articles (80.8%). URL presence does not prove every remote image still loads; this audit did not fetch all 366 URLs. The earlier detailed ingestion run backfilled two missing image URLs, and a live sample returned HTTP 200 with `image/jpeg`; no further recovery was attempted during this read-only audit. Image parsing, resolver, loading, failed-image, unsafe-URL, and fallback cases have tests. No unrelated or generated images were substituted.

Discover keeps category tabs and a searchable publisher selector. Category, source, and text filters remain URL-backed; the API only returns publishers with matching catalog articles. An empty publisher response is an ordinary empty state, while genuine API errors remain visible and retryable.

## Route, API, and AI review

Declared public routes are `/login` and `/register`. The remaining declared routes are protected by the workspace guard: `/`, `/dashboard`, `/discover`, `/recommendations`, `/saved`, `/search`, `/semantic-search`, `/assistant`, `/news-dna`, `/briefing`, `/topics`, `/topics-intelligence`, `/events`, `/events/:eventId`, `/article/:articleId`, `/sources`, `/categories`, `/analytics`, `/trends`, `/profile`, `/preferences`, `/privacy`, `/research`, `/admin`, `/notifications`, `/comparison`, and `/system`; unknown paths use the not-found page. There is no separate reading-history route.

Live API checks covered `/api/health`, article and category filters, `/api/v1/sources/available` with All/category/text filters, source detail, article `sourceId`, empty results, pagination, sort order, invalid-page validation, `/api/v1/news/status`, and semantic search. Pagination returned distinct 10-item pages in descending publication order; an invalid negative page returned HTTP 400. Authentication and protected API behavior are covered by the backend suite. Every route definition was inspected, but authenticated browser rendering of every page was not verified in this run.

The local database has no pgvector extension or embedding table. A semantic request returned `mode=keyword-fallback`, `reason=pgvector-unavailable`; it did not claim embedding similarity. Recommendations and News DNA use available catalog and reading activity rather than a trained predictive model. Briefing is an article digest, Copilot does not generate grounded answers, and topic/event grouping is keyword-based candidate discovery, not verified event clustering.

## Data safety and verification

No database migration or data-changing audit request was made. PostgreSQL remains at Flyway schema version 6 with 1 user, 1 saved citation, 453 articles, 187 sources, and 366 image URLs. The audit backend was started with scheduled ingestion, cleanup, and embedding backfill disabled.

- Backend Maven suite: **63 tests, 0 failures, 0 errors, 3 skipped**. The focused news/catalog integration suite also passed **5/5**.
- Frontend Vitest: **47 tests across 11 files passed**.
- Frontend TypeScript and production build: **passed**.
- AI service pytest: **3 tests passed**.
- Live PostgreSQL: Flyway validated six migrations; schema v6 remained current.
- Browser E2E: **not run**. Headless Edge could not start its GPU process in this environment (`GPU process isn't usable`), and no DevTools endpoint was available. No browser-rendering claim is made.

There is no `.git` directory in this workspace, so Git status and a baseline diff were unavailable. The root `.env` was read only for process configuration; no values were printed or changed.

## Known limits and manual browser check

The latest provider attempt lacks persisted per-feed diagnostics after process restart. World has no articles. Only 80.8% of stored articles currently have an image URL, and remote URL validity was not exhaustively checked. pgvector, generative Copilot, synthesized briefings, verified event clustering, password recovery, and notification delivery are not implemented. These states should remain explicit in the UI.

For a browser check, keep the backend on 8080 and Vite on 5180, sign in with an existing account, then open `/discover`. Switch through All, Business, Entertainment, Health, Politics, Science, Sports, Technology, and World; search for a publisher; select a publisher; change category; and use Retry after forcing a request failure. Confirm the World empty state is not shown as a publisher-service error. Then inspect Dashboard, Recommendations, Saved, Search, Semantic Search, Copilot, News DNA, Briefing, Topics, Events, Sources, an article detail page, Analytics, Notifications, Profile, Preferences, Privacy, Research, Admin, and System. Image fallbacks should remain compact when a publisher image is missing or blocked.

## Restart commands

The backend currently runs in read-only audit mode. These options apply when a new process starts. If a healthy backend is already listening, the launcher reuses it with its existing settings; stop that verified PulseAI process from its launch terminal before switching modes.

To start a new read-only audit instance with the cached Maven repository:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\scripts\Start-Backend.ps1 -ReadOnlyAudit -Offline -MavenRepository "$env:USERPROFILE\.m2\repository"
npm.cmd --prefix frontend run dev
```

To restore the `.env` ingestion schedule, stop the current audit instance and omit `-ReadOnlyAudit` after reviewing provider access and quota status. `-Offline` and `-MavenRepository` can remain when Maven Central is unavailable.
