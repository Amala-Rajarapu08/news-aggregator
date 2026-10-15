# PulseAI Final Submission Check

## Dashboard image and responsive repair — 2026-10-13

### Causes and completed fixes

- Dashboard selection used only the newest 12 articles. In the live catalog's newest 20, 8 were from ESPN; all nine categories occur within the newest 200. The Dashboard now loads pages of 100 until it covers the available nine categories (up to four pages), then selects 12 real articles by category and publisher counts. It keeps the catalog's newest-first order. The live sample had all nine categories and 12 publishers.
- Missing publisher image URLs used a large Sports SVG depicting a football field, repeated across unrelated Sports coverage, with a visible generic-art badge. The Sports SVG now uses neutral multi-sport artwork; all fallback art is displayed as a small category illustration on a quiet background. The visible badge was removed; a truthful accessible image label remains.
- The existing publisher-image path was reviewed end to end. RSS parsing reads image/media/enclosure metadata and image URLs in publisher-provided excerpts; GNews and NewsAPI image URLs are normalized; the page resolver checks publisher Open Graph, Twitter Card, and `link rel="image_src"` metadata when no image is present. Requests are limited and cached. Persisted `imageUrl` is included in `ArticleResponse` and mapped unchanged by the frontend. No third-party image URLs are guessed or fabricated.
- The shared `ArticleImage` remains in the Dashboard, Discover, Recommendations, Saved Articles, Search Results, and Article Details. Invalid or failed publisher URLs fall back to the category SVG. The live catalog currently has image URLs on 615 of 745 articles; 11 of the selected Dashboard 12 had HTTP(S) URLs. URL presence does not guarantee publisher hotlink access, so the fallback remains necessary.
- Responsive styles now keep article titles from overflowing, preserve consistent image ratios, stack article grids and Discover search controls on narrow screens, account for device safe areas, and leave scroll clearance for Copilot above mobile navigation and the last card actions. The sidebar remains a drawer at tablet widths.

### Verification for this repair

| Check | Result |
|---|---|
| Backend compile and H2 tests | Passed; 74 tests, 0 failures, 0 errors, 3 skipped |
| Frontend tests | Passed; 12 files, 53 tests |
| Frontend production build and typecheck | Passed; 1,670 modules built |
| Flyway | Test run validated all 8 migrations; existing PostgreSQL schema remains at V8. This repair adds no migration. |
| Live API smoke checks | HTTP 200 for categories (9), technology publisher listing (62), article pages, news status, and frontend root on port 5180 |
| Current news status | Latest recorded run fetched 638, inserted 14, backfilled 2 images, and recorded 1 provider failure; 615 of 745 articles have an image URL. |
| Browser viewport check | The isolated Edge runner could not connect to its local DevTools port. Its temporary test account was removed. A prior full route audit is recorded below; fresh visual checks at desktop, tablet, and mobile widths still need manual confirmation. |

The running frontend is available at [http://127.0.0.1:5180](http://127.0.0.1:5180). For submission, manually inspect Dashboard, Discover, Recommendations, Saved Articles, Search Results, and Article Details at desktop, tablet, and mobile widths, including external image loads and the Copilot launcher beside bottom navigation.

Files changed for this repair:

- `frontend/src/pages/DashboardPage.tsx`
- `frontend/src/pages/dashboardEditorialSelection.ts` and `.test.ts`
- `frontend/src/components/editorial/ArticleComponents.tsx` and `.test.tsx`
- `frontend/src/assets/category-illustrations/sports.svg`
- `frontend/src/styles/pulse.css`
- `backend/src/test/java/com/pulseai/ingestion/ArticlePageImageResolverTest.java`
- `docs/FINAL_SUBMISSION_CHECK.md`

Verification completed 2026-10-13 against the running PostgreSQL database, backend on port 8080, and frontend on port 5180.

## 1. Recovery and preserved work

- No reset, checkout, or regeneration of the interrupted source work was performed.
- The workspace has no Git metadata at its root, so `git status` and `git diff` cannot establish the exact previous 27-file change list or its `+368/-53` totals. The current files were inspected directly; the prior implementation and tests are present in the working tree.
- The existing account and saved citation remain. Before refresh, the database had 1 user, 453 articles, 187 sources, and 1 saved citation. It now has 1 user, 745 articles, 253 sources, and 1 saved citation. Article/source growth is from news ingestion; the migrations are additive.

## 2. Additional repairs completed

- Added Flyway V8 for the `General` category and included `general` in NewsAPI category mapping/configuration.
- Added the BBC World RSS feed; the live catalog now has World and General coverage.
- Marked ingestion successful when at least one provider or feed fetch succeeds, while retaining the prior success timestamp if every source fails.
- Kept image metadata lookup bounded and spread lookups across RSS feeds so one feed cannot consume the full budget. Existing positive and negative lookup caching remains in place.
- Added regression coverage for publisher listing, partial provider/feed failures, General category mapping, and the metadata lookup budget.

## 3. Compilation and automated tests

| Check | Result |
|---|---|
| Backend Maven test lifecycle (compile + H2 suite) | Passed; 74 tests, 0 failures, 0 errors, 3 skipped |
| Isolated PostgreSQL test suite | Passed; 73 tests, 0 failures, 0 errors, 2 skipped |
| Frontend unit tests | Passed; 12 files, 53 tests |
| Frontend production build and TypeScript check | Passed; 1,670 modules built |

The skipped backend tests are optional PostgreSQL/live-news or environment-specific tests; the PostgreSQL run exercised the separate PostgreSQL integration tests.

## 4. Database migrations and data preservation

- The configured local database is at Flyway schema V8.
- On a fresh isolated PostgreSQL 18 database, Flyway validated all 8 migrations and applied V1 through V8. The PostgreSQL suite then passed and its temporary server was stopped.
- V8 adds the General category with a conflict-safe insert. V1 through V7 were left intact. No table was dropped or truncated.
- The live database still has its original user and saved citation.

## 5. Image handling

- 615 of 745 cached articles currently have publisher image URLs.
- Live publisher coverage is partial: ESPN has images on 13 of 58 cached articles; TechCrunch has images on 7 of 37. Their current RSS responses did not supply direct image URLs, so the bounded page-metadata lookup only improves coverage when the publisher exposes usable metadata.
- The browser showed a lead-story publisher image or its category illustration fallback. Frontend tests confirm distinct local SVG fallbacks for all nine categories and the fallback disclosure that the art is not a news photograph.
- External publisher URLs can still be unavailable, block hotlinking, or lack image metadata; fallback rendering covers those cases.

## 6. Publisher API and filters

- `GET /api/v1/sources/available?category=business` returned HTTP 200 with 60 publishers.
- The authenticated browser loaded the publisher picker, selected NASA under Science, and returned matching articles while retaining the category filter. The previous “Publishers unavailable” error was not reproduced.
- Browser verification confirmed category and publisher filters can be combined.

## 7. News ingestion

- Scheduled ingestion is enabled with a six-hour interval. The last recorded refresh was `2026-10-13 16:45:35Z` (`22:15:35` local time).
- That run fetched 638 items, inserted 14, skipped 479 duplicates and 145 invalid items, backfilled 2 images, and recorded 1 provider failure. `lastSuccessfulRefreshAt` advanced because other sources completed successfully.
- All 9 configured RSS feeds returned HTTP 200 in the latest persisted diagnostics, including BBC World, ESPN, TechCrunch, PBS, NASA, NIST, CISA, and SEC feeds. Individual feeds with no new articles did not stop ingestion.
- GNews remains configured but returned HTTP 403. This is the one live provider error; RSS ingestion continued and the NewsAPI provider was configured and its live request previously returned HTTP 200.

## 8. Category coverage

All nine categories have stored articles and worked through the browser category filter:

| Category | Articles |
|---|---:|
| Business | 119 |
| Entertainment | 104 |
| General | 10 |
| Health | 78 |
| Politics | 25 |
| Science | 98 |
| Sports | 141 |
| Technology | 145 |
| World | 25 |

## 9. Browser and API functional verification

Headless Edge tested the frontend at `http://127.0.0.1:5180`; all scripted browser assertions completed successfully.

- Registered and authenticated a temporary account, then removed it and its related rows. The original user/citation counts were restored.
- Loaded Dashboard, Discover, all nine category filters, Recommendations, Saved Articles, Search, Semantic Search, Pulse Copilot, News DNA, Daily Briefing, Topics, Events, Sources, Article Details, Profile, Preferences, and the other declared app routes.
- Verified category and source filtering, keyword results and no-results state, bookmark persistence and display on Saved Articles, explicit-category recommendations, profile and preference persistence, reading-history analytics, article context and publisher-excerpt citations in Copilot, and logout/session revocation.
- Checked Discover at 390px width; no horizontal overflow was detected.
- Local smoke requests returned HTTP 200 for backend health, categories, news status, business publishers, and the frontend root. World and General article queries returned 25 and 10 results.

Events currently displays an honest no-event-data state. Topics currently exposes the nine category links while the editorial topic taxonomy is empty. Daily Briefing presents recent source-attributed articles and does not claim generated AI synthesis.

## 10. Remaining errors and limitations

- GNews returned HTTP 403 during the latest provider request. Check the account quota/subscription if GNews coverage is required; the other feeds continue to ingest.
- pgvector is not installed in the configured PostgreSQL instance. Semantic Search explicitly uses its keyword fallback; it does not provide embedding similarity on this database.
- ESPN and TechCrunch image URL coverage is incomplete where RSS/page metadata has no usable image.
- Event clustering/timelines, automatic briefing delivery, and generated Copilot answers are not connected features. The UI reports their current unavailable/planned states rather than presenting invented results.
- The exact interrupted Git diff cannot be recovered because this workspace has no `.git` directory.

## 11. Startup and verification commands

Run these from the project root in PowerShell. `Start-Backend.ps1` checks port 8080 and reuses a healthy PulseAI backend rather than starting a duplicate.

```powershell
.\scripts\Start-Backend.ps1
npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 5180
```

Optional AI embedding service, when needed:

```powershell
Set-Location ai-service
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --ws none
```

Verification commands:

```powershell
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
.\scripts\Verify-Postgres.ps1
```

## 12. Final image and card pass — 2026-10-14

### Findings and repairs

- The small fallback artwork was caused by a final `.article-image-fallback-art` rule that limited each full-frame SVG to 86px (76px on narrow screens). The surrounding image container remained full-size, which left a large empty area. The SVG now fills the image container with `object-fit: cover`; category-tinted surfaces and three deterministic treatments vary repeated fallbacks while keeping them clearly illustrated.
- Publisher images continue to use validated HTTP(S) URLs from RSS media/enclosures, GNews/NewsAPI image fields, or publisher page metadata (`og:image`, Twitter Card image tags, and `link rel="image_src"`). The metadata resolver caches positive and negative results and stays within its per-run limits. Missing image URLs usually mean the feed/API item and publisher page did not offer usable metadata; an external host can also refuse a browser image request. Those cases use the local category art; no article-image URLs or photographs are fabricated.
- The existing `image_url` persistence, `ArticleResponse.imageUrl`, frontend article mapping, shared `ArticleImage`, category/publisher-balanced Dashboard selection, Discover filtering, and pagination were preserved. Cards without descriptions keep a compact copy area. The fallback remains identified as an illustration to assistive technology, without a visible badge.

### Final checks

| Check | Result |
|---|---|
| Frontend tests | Passed: 12 files, 54 tests |
| Frontend type check and production build | Passed: 1,670 modules built |
| Backend Maven test lifecycle, H2 | Passed: 74 tests, 0 failures, 0 errors, 3 skipped |
| Isolated PostgreSQL suite | Passed: 74 tests, 0 failures, 0 errors, 2 skipped |
| Flyway on isolated PostgreSQL 18 | Passed: V1–V8 validated and applied; schema verified at V8 |
| Local smoke checks | Backend health, business publishers, categories, and frontend root all returned HTTP 200 |

The PostgreSQL check used the project verification script and a newly created temporary cluster under `.runtime`; the configured application database was untouched and the temporary cluster was removed afterward. The skipped tests are the Docker-backed PostgreSQL Testcontainers case and the opt-in live-news test. Flyway emitted an advisory because this installed version has verified compatibility through PostgreSQL 17, while the temporary server is PostgreSQL 18.6; validation and all migrations nevertheless succeeded.

The backend startup script was invoked with `-ReadOnlyAudit -Offline -MavenRepository C:\Users\gutta\.m2\repository`; it found the already-running PulseAI backend healthy on port 8080 and reused it, so the audit flag did not restart or change the existing process. The frontend at `http://127.0.0.1:5180` was also already serving the project; a second Vite launch exited when it detected port 5180 in use. No duplicate service was left running. The desktop browser processes could not be controlled from this session: project Playwright packages are absent and the local Edge debugging endpoint is unavailable. The changed image treatment therefore passed component/build checks but still needs a visual review in the user's browser at desktop and mobile widths.

Additional source files edited in this final pass: `frontend/src/components/editorial/ArticleComponents.tsx`, `frontend/src/components/editorial/ArticleComponents.test.tsx`, and `frontend/src/styles/pulse.css`.

## 13. Live image pipeline investigation and bounded backfill - 2026-10-14

### Evidence from the running application

- The backend health route, article catalog API, and frontend root returned HTTP 200. The catalog API returned 745 article rows; the ingestion status SQL count and API DTOs agree that 615 have an `imageUrl` and 130 have a null `imageUrl`.
- Image coverage by category was: Business 100/119, Entertainment 101/104, General 7/10, Health 69/78, Politics 25/25, Science 92/98, Sports 89/141, Technology 107/145, and World 25/25.
- Representative API rows with publisher URLs included ESPN CDN (Sports), TechCrunch (Technology), BBC (World), AP (Business), and NASA (Science). Rows with null URLs included ESPN (Sports), TechCrunch and NASA (Technology/Science), WSJ (Business), and PennLive (Health). This verifies the backend returns stored nulls and URLs as-is; it does not establish that a remote image host currently serves each URL.
- Persisted RSS diagnostics recorded ESPN with 41 parsed items and 0 feed image candidates, and TechCrunch with 20 parsed items and 0 feed image candidates. PBS had 20/20 candidates, BBC 25/25, and NASA 9/10. RSS media/enclosure/content extraction tests pass. NewsAPI `urlToImage` and GNews `image` mapping tests pass. Raw provider bodies are not retained, so individual API payload fields could not be inspected after ingestion.
- `Article.imageUrl`, the existing `image_url` column, `ArticleResponse.imageUrl`, and `UserMapper` all preserve and return the same stored value. The frontend shared `ArticleImage` accepts only HTTP(S) URLs, displays the publisher URL, and switches to the full-size category illustration on `onError`. Its loading, error, category-fallback, and unsafe-URL cases are covered by frontend tests.
- Frontend and article API responses had no `Content-Security-Policy` header, so this deployment has no CSP `img-src` rule blocking publisher images.

### Confirmed gap and changes

- Historical null-image articles were not revisited as a catalog-wide task. The existing resolver only saw items returned by an ingestion batch and was constrained by its per-run metadata lookup budget. ESPN and TechCrunch direct RSS image metadata is absent in the persisted feed diagnostics, so those articles need publisher page metadata to be available to improve coverage.
- Added `ArticleImageBackfillService`, which revisits existing `image_url IS NULL` articles in stable UUID keyset order. It processes six candidates per hour by default, clamps configured batches to 12, scans at most 128 candidates per run, uses the resolver's six-hour positive/negative cache, and writes only when the database row still has no image. It adds no schema migration and does not replace or delete article records.
- Publisher page metadata requests now use one bounded request at a time (8-second request timeout) and follow at most three redirects. Every redirect must remain on the configured publisher host or its subdomain and pass the public-address check. Provider API redirects remain disabled. Existing RSS media/enclosure, OG, and Twitter-card extraction remains in place.
- No image proxy was added. Direct host failures could not be demonstrated from this environment, so a cache/proxy was not justified by the evidence collected.

### Verification and outstanding limits

- Backend full H2 suite: 82 tests passed, 0 failures/errors, 3 skipped. The 19 focused resolver/client/backfill/persistence tests also passed. Frontend: 54 tests passed; TypeScript and production build passed (1,670 modules).
- The database-level keyset query test passed on H2. A new isolated PostgreSQL attempt could not initialize a temporary server because `initdb` returned restricted-token error 87; the configured application database was not touched. The prior PostgreSQL V1-V8 validation recorded above remains the latest successful full PostgreSQL verification.
- Outbound TCP to public image hosts is denied by this environment. Requests to representative ESPN, TechCrunch, BBC, AP, PBS, and NASA image hosts failed before receiving HTTP headers, so no 200/403/404, redirect chain, content type, or response body can be reported. Browser devtools are unavailable here, so live browser rendering was not verified. No proxy or successful image delivery is claimed.
- The current backend on port 8080 was left running and healthy; it is the already-started process and has not loaded this new scheduled service. Newly enriched rows from this service in this session: 0. The catalog still reports 130 null image URLs. Restart the verified PulseAI backend to load the change; its first bounded backfill runs after 60 seconds when `NEWS_IMAGE_BACKFILL_ENABLED` and `NEWS_IMAGE_METADATA_ENABLED` are enabled (both default to true). The next hourly batches continue from the in-process cursor.
- The previously serving frontend at `http://127.0.0.1:5180` remains HTTP 200. Browser inspection of actual external images and response statuses requires a network-enabled browser/session.
