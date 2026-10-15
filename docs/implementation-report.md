# PulseAI implementation report

## Earlier integrated checkpoint — 2026-10-13

This checkpoint predates the final General-category and World-feed repairs. Its live counts and migration version are historical. Use the [final submission check](FINAL_SUBMISSION_CHECK.md) as the current status. Older snapshots below describe earlier project states.

### Problems found and changes made

- The running backend predated the current `.env` and source changes. It was confirmed as PulseAI, health checked, and restarted after tests. `scripts/Start-Backend.ps1` then loaded the local settings; the API reports `gnews`, `news-api`, and `rss` as configured providers.
- NewsAPI needed a default `/v2/top-headlines` endpoint and a country parameter when its URL was blank. Its key is sent in the `X-Api-Key` header. A live business request returned HTTP 200 with an image. GNews uses the documented `X-Api-Key` header; the live request returned HTTP 403. GNews documents 403 as quota exhaustion or subscription expiry, so the key was not classified as invalid. See [GNews authentication](https://docs.gnews.io/authentication), [GNews errors](https://docs.gnews.io/error-handling), and [NewsAPI authentication](https://newsapi.org/docs/authentication).
- The app now preserves each provider's original article link alongside the canonical deduplication URL. Flyway V5 backfilled existing article and saved-citation links without deleting rows.
- FastAPI now loads `sentence-transformers/all-MiniLM-L6-v2` on CPU and returns normalized 384-dimensional vectors. The backend uses that same model for queries and article indexing, with scheduled bounded backfill and pgvector cosine search. The local PostgreSQL 18 installation has no `vector` extension files, so no embedding table can be created there. The semantic endpoint correctly reports its keyword fallback instead of claiming semantic ranking.
- RSS/API image metadata, safe image URL filtering, article DTO mapping, frontend image display, and image-load fallback are covered by the implementation and tests. One persisted image returned HTTP 200 with `image/jpeg`.
- The root `.env` was not edited, replaced, or printed. It contains duplicate database username/password definitions and three JWT definitions, including a malformed intermediate Base64 value; the local launcher applies the last definition, and Spring Boot started successfully. `NEWS_API_BASE_URL` is blank, so the provider's official default endpoint is used. Consolidate duplicate keys manually when convenient while preserving the effective final values.

### Live PostgreSQL and provider results

The existing PostgreSQL database remains on schema version 6. It still has one user and one saved citation. Read-only verification found 454 articles, 187 source records, and 367 articles with image URLs. The article distribution contains 183 distinct publisher names.

| Category | Articles |
|---|---:|
| Business | 70 |
| Entertainment | 63 |
| Health | 54 |
| Politics | 20 |
| Science | 66 |
| Sports | 90 |
| Technology | 91 |
| World | 0 |

The startup ingestion run fetched 575 entries, inserted 374, skipped 80 duplicates, rejected 121 stale/invalid entries, backfilled 2 missing image URLs, and recorded 8 provider failures. All eight RSS feeds returned HTTP 200. The latest attempt was `2026-10-13T08:28:39Z`; the prior fully successful refresh remains `2026-10-13T04:57:13Z`. The NewsAPI live request succeeded; GNews returned 403. World remains empty because the live providers did not return matching items; unrelated stories were not recategorized to fill it.

The live `/api/v1/search/semantic` endpoint returned `mode=keyword-fallback` and `reason=pgvector-unavailable` for the NASA query, with 17 text-matching results. Article embeddings indexed: **0**. The AI service itself is running at `http://127.0.0.1:8000`; its health route and real embedding endpoint both returned HTTP 200. A real vector had 384 dimensions and norm 1.0.

### Verification results

- Backend H2 suite: **55 tests, 0 failures, 0 errors, 3 skipped**.
- Backend isolated PostgreSQL 18 suite: **55 tests, 0 failures, 0 errors, 2 skipped**. Flyway migrations through V6 succeeded; pgvector was absent, so V6 left the optional vector table uncreated. Existing database services were not touched by this test.
- Frontend Vitest: **10 files, 28 tests passed**.
- Frontend production build and TypeScript check: **passed**.
- AI service pytest: **3 tests passed**. The real Sentence Transformers request also passed with a normalized 384-dimensional result.
- Live image check: **HTTP 200, `image/jpeg`**. Live category filters matched the database counts above. Existing account and saved-citation counts remained 1 each after migration.

### Files changed

- Backend: `GNewsProvider.java`, `NewsApiProvider.java`, `ProviderHttpClient.java`, `ArticleNormalizer.java`, `ArticlePersistenceService.java`, `Article.java`, `SavedArticle.java`, and `UserMapper.java`.
- Search and embeddings: `SearchController.java`, `SemanticSearchResponse.java`, `AiEmbeddingClient.java`, `ArticleEmbeddingService.java`, `PgVectorSearchRepository.java`, `PostgresVectorSchema.java`, `SemanticSearchService.java`, and `ArticleCatalogService.java`.
- Database/config: `V5__preserve_original_article_urls.sql`, `V6__enable_pgvector_article_embeddings.sql`, `application.yml`, and `application-test.yml`; backend provider, API, migration, and auth tests were updated.
- Frontend: `frontend/src/services/searchService.ts`, `frontend/src/pages/ResearchPages.tsx`, and `ResearchPages.test.tsx`.
- AI service: `ai-service/app/main.py`, both requirements files, `tests/test_embeddings.py`, and its README.
- Project configuration/docs: `.env.example`, `docker-compose.yml`, `README.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `docs/architecture/news-ingestion.md`, and this report. The local `.env` was left unchanged.

### Commands to run PulseAI

From the project root in PowerShell, start the backend and frontend using the existing local database and `.env`:

```powershell
.\scripts\Start-Backend.ps1
npm.cmd --prefix frontend run dev
```

Start the AI service in another terminal:

```powershell
Set-Location ai-service
if (-not (Test-Path -LiteralPath .venv)) { py -3.10 -m venv .venv }
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
$env:HF_HOME = Join-Path (Get-Location) '.runtime\huggingface'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --ws none
```

The existing app instances were verified at frontend `http://localhost:5180`, backend `http://localhost:8080`, and AI service `http://127.0.0.1:8000`. To activate genuine vector search, install pgvector files built for local PostgreSQL 18, then make `CREATE EXTENSION vector` available to the database role. The backend will create the embedding schema and start indexing once the extension is available.

> Earlier checkpoints below are retained as history. Their article counts, provider configuration, and semantic-search status are superseded by this section.

## Earlier live follow-up — 2026-10-13

Historical checkpoint before API keys were applied to the running backend and before the current provider/embedding integration.

The live ingestion cycle made five HTTP 200 requests and parsed 125 entries: NASA 10, SEC 25, NIST News 40, NIST Health 40, and CISA 10. It inserted 0 new rows, skipped 4 existing NASA duplicates, rejected 121 entries (120 outside the 48-hour window and 1 without a publication timestamp), and reported 0 provider failures. All five feeds therefore worked as transport/parser inputs, but only NASA had any cached articles. The latest SEC/NIST/CISA items were outside retention; a configured category is not evidence that it has fresh articles.

The unchanged PostgreSQL catalog contains 4 articles from NASA, all Science, with 0 image URLs. The RSS parser saw 9 image candidates across the feed, but candidate presence is not a license check; duplicate articles do not receive an image-only rewrite, so the four cached records still use the category fallback. NASA warns that some hosted imagery belongs to third parties. The database still has 1 user and 1 saved-article reference; no user or bookmark rows were removed. Flyway schema version 4 remains current. The status endpoint now correctly reports `{"science":4}` and `{"NASA":4}`; retention is 48 hours and the cap is 2,000. All eight category filters were queried: Science returns 4; Business, Entertainment, Health, Politics, Sports, Technology, and World return 0. `All` returns 4. The combined Science + NASA source UUID + `q=NASA` query returns 4.

The configured feeds are `science|https://www.nasa.gov/feed/`, `business|https://www.sec.gov/news/pressreleases.rss`, `technology|https://www.nist.gov/news-events/news/rss.xml`, `health|https://www.nist.gov/news-events/health/rss.xml`, and `technology|https://www.cisa.gov/news.xml`. They are official-domain feeds; [SEC publishes its press-release RSS link](https://www.sec.gov/newsroom/press-releases?page=0), and [NIST explicitly permits websites to display its RSS content](https://www.nist.gov/coo/nist-rss-feeds). Feed and image reuse rights remain publisher-specific, so the app stores only bounded metadata and external image URLs; no image binaries are copied. No keyed API provider was live-tested because the local API keys are blank.

Backend regression suite: 50 tests, 0 failures, 0 errors, 3 environment-gated skips. Frontend suite: 10 files, 26 tests passed; production build and TypeScript check passed. The active Discover route returned 200, as did the Vite-proxied articles and status APIs and `/favicon.svg`. The new Discover tests cover combined filters, retained filters during search, and an ingestion-aware empty state; image fallback rendering has a separate component test.

**Initial checkpoint:** 2026-10-12  
**Latest news/search checkpoint:** 2026-10-13

## News ingestion, image, filter, and search follow-up — 2026-10-13

### Measured baseline and root causes

The read-only PostgreSQL snapshot after the updated backend's startup refresh contains **4 articles**, all from **NASA**, and all categorized as **Science**. Business, Entertainment, Health, Politics, Sports, Technology, and World contain zero articles. All four have descriptions; none have valid image URLs. There are no uncategorized rows, duplicate normalized canonical URLs, embedding tables, or vector indexes. One pre-existing user account remains; the temporary browser QA account was removed.

Only NASA appears because `NEWS_RSS_FEEDS` has one NASA URL. GNews and NewsAPI.org providers already existed in the code, but neither has a configured API key; NewsAPI.org also requires an endpoint. Category browsing is empty because only NASA Science articles are currently stored. This is a coverage/configuration limit, not a category-filter defect.

The NASA source UUID filter and its Science combination return all 4 rows. The previous search query only looked in titles and descriptions, so `q=NASA` returned zero when “NASA” appeared only in publisher metadata. There is no embedding model, vector table/index, semantic endpoint, or semantic retrieval implementation; the former semantic page did not issue a search. The NASA feed supplied no images for its four stored articles. RSS parsing already handled some direct media tags, but not nested Media RSS groups, Atom image links, relative image URLs, or reliable rejection of video/unsafe media.

### Changes made

- Expanded catalog `q` matching to article title/description, publisher name/domain, and category name/slug. Source UUID and category filters remain conjunctive and are now covered together with publisher-only search in the integration test.
- Extended GNews and NewsAPI.org ingestion to fetch configured category lists, preserve the category supplied by the selected provider category, report partial category failures, validate category values, and retain publisher image/author/source metadata. GNews category calls are paced one second apart for its documented free-plan rate limit.
- Improved RSS extraction for nested Media RSS groups, image MIME-typed enclosures, Atom image links, safe relative images, and image tags in feed excerpts. Non-image media and unsafe image schemes are rejected. No publisher pages are fetched for Open Graph scraping.
- Updated article image alt text and loading feedback. Missing/broken images keep the neutral publisher fallback.
- Changed the semantic-search route to explain that embeddings are unavailable and to run a clearly labeled catalog keyword fallback including publisher/category metadata. It does not present keyword results as semantic matches.
- Updated `.env.example` and the ignored local `.env` with category lists and a six-hour schedule. The local `.env` still has no GNews or NewsAPI.org key, so it has not populated broader coverage. Restarting the normal backend triggered its enabled NASA RSS schedule and inserted one real provider article; no fixtures or manual SQL article edits were used. No Flyway migration was added or changed.

### Current catalog and freshness

| Measure | Actual PostgreSQL value |
|---|---|
| Articles | 4 |
| Sources with articles | 1: NASA (4) |
| Categories with articles | Science (4); seven categories (0) |
| Valid article image URLs | 0 of 4 |
| Articles with descriptions | 4 of 4 |
| Semantic embeddings indexed | 0; no embedding/vector schema exists |
| Last successful refresh | 2026-10-13 00:20:30 +05:30 |
| Last recorded fetch | 10 fetched, 1 inserted, 3 duplicate skips, 6 invalid/outside retention, 0 failures, 1 provider |

The successful-fetch timestamp means provider retrieval succeeded; it does not claim news is comprehensive. The latest startup run inserted one real NASA article; current publisher/category fallback search returns all 4 NASA rows for `NASA`.

### Feature status

| Feature | Status | Evidence/limit |
|---|---|---|
| NASA RSS ingestion, deduplication, and freshness status | WORKING | Live status and existing three records; broad categories require more providers |
| GNews and NewsAPI.org category adapters | FIXED, NOT CONFIGURED | Mocked parsing/category/partial-failure tests pass; no keys available for live requests |
| Category and source filters, including combined filters | WORKING | Existing source UUID filter returns 4/4; H2, isolated PostgreSQL, and browser checks pass |
| Publisher/category-aware keyword search | FIXED | `NASA` returns all 4 rows through the actual API and UI fallback |
| Semantic retrieval | NOT CONFIGURED | No embeddings, vector index, or semantic service; route says so and uses labeled keyword fallback |
| RSS image metadata extraction and UI fallback | FIXED | Parser and browser checks pass; current database has no publisher image URLs |
| Article details and original-source links | WORKING | Existing browser article workflow and source-safe URL handling remain |
| Refresh scheduler/status | WORKING | Local schedule enabled for six hours; only NASA is configured and its latest recorded fetch inserted one new article |

### Latest verification

- Backend H2 suite: **50 tests, 0 failures, 0 errors, 3 skipped**.
- Backend isolated PostgreSQL suite: **50 tests, 0 failures, 0 errors, 2 skipped**. This used the temporary verification cluster, not the configured `pulseai` database.
- Focused provider/parser/catalog integration run: **15 tests passed**.
- Frontend Vitest: **9 files, 22 tests passed**.
- Frontend production build and TypeScript check: passed; Vite transformed 1,659 modules.
- Headless Edge against the actual PostgreSQL catalog on isolated ports 5183→8082: **19 assertions passed, 0 JavaScript/console errors**. This included registration, refresh/session restoration, combined Science+NASA filtering, NASA metadata fallback, article detail, save persistence, account preferences/history, and logout. The temporary account and dependent rows were removed and the existing account was retained.
- Final read-only DB verification confirmed 4 articles, 1 source, 4 Science records, 0 image URLs, 1 pre-existing user, and no QA user remaining.

### Required manual configuration

For broader live data, add an API key directly to the ignored `.env` and restart Spring Boot:

- GNews: set `GNEWS_API_KEY`; `GNEWS_CATEGORIES` is already set to the supported World/Nation, Business, Technology, Entertainment, Sports, Science, and Health feeds. The currently documented free plan has a 12-hour delay and 10 items per category request. GNews has no Politics category.
- NewsAPI.org: set `NEWS_API_KEY` and `NEWS_API_BASE_URL`, for example `https://newsapi.org/v2/top-headlines?country=in`. Its configured categories cover Business, Entertainment, Health, Science, Sports, and Technology; its top-headlines category list does not include Politics or World.
- For Politics and any other taxonomy gaps, configure a feed/provider whose terms permit this app's storage/display, or use a licensed provider with matching metadata. Do not map unrelated categories to make sections appear full.

Keep keys server-side in `.env`, never in React/Vite variables or checked-in URLs. Provider key plans and content/image terms still apply. The API sources and missing keys are described in [the ingestion guide](architecture/news-ingestion.md).

## Recovered project state

The existing workspace contains a React/Vite frontend, Spring Boot API, PostgreSQL/Flyway persistence, and a health-only FastAPI service. The active React routes use persisted account, article catalog, categories, sources, saved citation, preference, and reading-history APIs. The editorial dashboard, floating Copilot, and Pulse Lens are present.

The Copilot searches cached headlines and publisher excerpts and links to the original sources. It does not call an LLM or fabricate analysis. Lens-related coverage is labeled as keyword candidates; verified event grouping, source comparisons, semantic retrieval, and RAG remain future work.

This workspace has no `.git` directory, so `git status` and the preceding session's full diff are unavailable. The root `.env.example` was missing and has now been added. The ignored `.env` remains local and contains the database credentials plus the local RSS configuration; no credentials are recorded in this report.

## Image handling and test coverage

- RSS parsing and normalization tests cover articles with no publisher image; the normalized image URL stays null.
- An independent frontend render test verifies the publisher fallback for missing images. Existing tests separately cover invalid URLs and browser image-load failure.
- Production article cards use a valid publisher image URL and switch to a branded publisher fallback when an image is missing, unsafe, or unavailable.
- Image binaries are not downloaded or stored by the ingestion service.

## Existing PostgreSQL database

The configured `pulseai` database on `localhost:5432` is PostgreSQL 18.6. Read-only inspection before initial startup found no tables or Flyway history; no existing tables were dropped, truncated, or reset. The live database was rechecked during this audit using the ignored `.env` credentials.

Spring Boot then validated and applied the existing V1–V4 migrations to this database. Subsequent startups validated all four migrations and reported schema version 4 as current. Hibernate validated the resulting schema. The V1–V4 migration files were not edited. Flyway logged that PostgreSQL 18.6 is newer than the latest PostgreSQL version it has tested (17); the migrations nevertheless completed and validated successfully.

Flyway V1–V4 validate successfully and schema version 4 is current; Hibernate validates the mappings. At the 2026-10-12 checkpoint, the database contained eight categories, one NASA publisher, three cached articles (all categorized as Science), and zero users. The three Science assignments were made only on previously uncategorized NASA rows after setting the feed's explicit `science|` prefix. The 2026-10-13 browser QA account was deleted after verification; one pre-existing user account remains.

## PostgreSQL-backed authentication

Against the actual PostgreSQL database, an isolated headless Edge session completed registration, current-user lookup, profile update, preference persistence, login, refresh-token rotation, reload/session restoration, saved-article persistence, and logout. A previously issued access token was rejected after logout. The temporary account and related saves, history, preferences, and sessions were removed afterward. Unauthenticated access to the protected current-user route returned 401.

The implementation uses BCrypt password hashing, signed access tokens, and database-backed refresh sessions. The workflow was exercised manually against PostgreSQL; this does not replace the environment-gated PostgreSQL integration tests.

## Live news and frontend integration

The ignored local `.env` is configured with the official NASA RSS feed `science|https://www.nasa.gov/feed/`, scheduled refresh enabled, a six-hour interval, and the existing 48-hour/2,000-article cache bounds. The configured `science|` prefix maps feed articles to the seeded Science category. Earlier live RSS verification returned 10 items, inserted 3, later skipped duplicates, and filtered entries outside retention; that 2026-10-12 checkpoint had 3 cached NASA articles and no publisher image URLs; the 2026-10-13 startup refresh added one real article, bringing the current count to 4. The app stores bounded metadata and external image URLs, not image binaries or full article bodies.

The NASA feed is a narrow science/NASA source, not a broad general-news lineup. NASA's [RSS feed page](https://www.nasa.gov/rss-feeds/) lists its news-release and topical feeds. NASA's [media guidance](https://www.nasa.gov/nasa-brand-center/images-and-media/) permits factual informational use of NASA material under its conditions, but warns that some NASA-hosted images are third-party copyrighted and NASA's use does not grant reuse rights. RSS image URLs are not individually license-checked by this application; verify rights before any wider or commercial redistribution.

The backend health endpoint, categories (8), sources (1), articles (4), news status, and Science-filtered articles returned successfully. The active Vite app on port 5182 proxies to the project backend on port 8080. The public API health endpoint is available; Actuator health remains protected by design and is not publicly exposed.

### Authentication 403 root cause and correction

The 5182 browser requests were reaching a stale Vite proxy/backend configuration whose CORS allowlist did not include `http://127.0.0.1:5182`. Spring's CORS filter rejected both refresh and login before authentication or database code ran. The old processes on 8080 and 8081 also held startup-time allowlists that did not include 5182; editing `.env` does not change a running Spring process. A direct request to the corrected backend with the allowed origin and no refresh cookie returns 401, as intended. After restarting the backend on 8080 and Vite on 5182 with its proxy explicitly targeting 8080, the proxied refresh also returns 401, catalog routes return 200, and protected account routes without a bearer token return 401.

The public auth matchers remain scoped to register/login/refresh/logout. CSRF behavior, bearer-token authorization, refresh-cookie protections, and cross-user access controls were not weakened. The refresh cookie remains HttpOnly, SameSite=Strict, and scoped to `/api/v1/auth`; local HTTP disables Secure through the local profile. `AuthAndUserApiTest` now covers the allowed-origin vs rejected-origin response and cookie attributes.

GNews and other keyed providers remain optional; no API key was configured or verified. The RSS adapter, bounded retention, category filtering, keyword search, deduplication, missing-image behavior, provider error reporting, and actual refresh status are implemented.

## Verification results at the 2026-10-12 checkpoint

| Check | Result |
|---|---|
| Backend RSS parser and article normalizer tests | 8 passed, 0 failed |
| Full Spring Boot suite on the H2 test profile | 42 tests, 0 failures, 0 errors, 3 skipped |
| Focused H2 authentication/security suite | 7 tests, 0 failures, 0 errors, 0 skipped |
| Full Spring Boot suite on isolated PostgreSQL | 42 tests, 0 failures, 0 errors, 2 skipped |
| Focused PostgreSQL authentication/security suite | 7 tests, 0 failures, 0 errors, 0 skipped |
| Frontend Vitest suite | 8 files, 19 tests passed |
| Frontend TypeScript check and production build | Passed |
| AI service health test | 1 passed |
| Actual PostgreSQL migration and schema validation | V1–V4 validated/applied; schema version 4 current |
| Actual PostgreSQL auth/browser workflow | Registration, profile, preferences, login, refresh, saved story, reload, logout/revocation passed; temporary account removed |
| Live RSS smoke test on isolated PostgreSQL | 10 fetched, 3 inserted, 3 duplicate skips, 7 invalid/outside retention, 0 feed failures |
| Browser route, filter, and search audit | 18 assertions passed in headless Edge; every declared route rendered; combined category/source and keyword/no-results checks passed; 0 JavaScript/console errors |
| Vite-to-Spring-to-PostgreSQL API path | Verified on local ports 5182 → 8080 |

The full H2 suite's skipped tests are environment-gated PostgreSQL upgrade, live RSS, and Docker/Testcontainers cases. The full isolated PostgreSQL suite still skipped the opt-in live RSS and Testcontainers cases; the separate live RSS test and actual local database browser flow were run successfully.

## Remaining work

- Add more news publishers only after checking each feed's terms and image/content rights. The current NASA source is science-focused and does not provide broad general-news coverage.
- The current assistant is source-linked catalog/excerpt search, not an LLM/RAG system. Evaluated NLP, embeddings, grounded responses, event grouping, and verified source comparisons are not implemented.
- The interface provides keyword, category, and publisher filters with combined filtering. Date range parameters and newest-first sorting exist at the API layer, but the UI has no date/sort controls; pagination beyond the first page was not exercised because only three articles were cached.
- Password reset, email verification, notification delivery, event clustering, semantic retrieval, and admin telemetry are not implemented. The semantic route now reports its degraded keyword fallback; other pending pages show explicit empty states. The AI service only exposes health and is not used to generate article answers.
- Production preparation remains: abuse throttling, HTTPS/cookie deployment settings, managed signing keys, CI/scanning, backup/restore checks, distributed ingestion coordination, durable provider quota accounting, and reviewed account deletion/retention.
- Rotate local credentials before production use. The database password and JWT signing value were supplied in the conversation; do not reuse the JWT value for a production deployment.

## Local run commands

The ignored `.env` is already populated. From the project root in a PowerShell terminal, start the backend on Vite's proxy target, port 8080:

```powershell
$env:SERVER_PORT = '8080'
.\scripts\Start-Backend.ps1
```

In a second PowerShell terminal, run Vite on port 5182 and point its proxy to the backend:

```powershell
$env:VITE_BACKEND_PROXY_TARGET = 'http://127.0.0.1:8080'
npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 5182 --strictPort
```

Open `http://127.0.0.1:5182`. Both processes were restarted and the Vite-to-Spring-to-PostgreSQL path was verified on these ports. Do not use Compose for this workflow; Compose provisions a separate PostgreSQL database.
