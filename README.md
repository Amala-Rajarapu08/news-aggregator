# PulseAI

**Understand the news, not just the headlines.**

PulseAI connects the existing React editorial workspace to Spring Boot and PostgreSQL. Accounts, saved citations, profiles, preferences, reading history, RSS and API news, publisher images, catalog search, recommendations, Pulse Copilot, and Pulse Lens use persisted backend data.

Copilot searches the current catalog and shows publisher excerpts with citations; it does not generate AI answers. The FastAPI service returns real normalized Sentence Transformers embeddings. Semantic retrieval only reports pgvector similarity when the database vector extension and index are available. The current PostgreSQL 18 installation has no pgvector extension, so `/api/v1/search/semantic` clearly reports a keyword fallback. RAG, event clustering, generated timelines, and perspective analysis remain future milestones.

See the [final submission check](docs/FINAL_SUBMISSION_CHECK.md) for the latest measured status and verification results. The [implementation report](docs/implementation-report.md) contains earlier audit notes.

## Run with your existing local PostgreSQL

Prerequisites: Java 17+, Maven 3.9+, Node 22.12+ (Node 24 was used), and PostgreSQL. Docker is optional. Run commands from the project root in PowerShell.

```powershell
if (-not (Test-Path -LiteralPath .env)) { Copy-Item -LiteralPath .env.example -Destination .env }
notepad .env
```

For a new environment, set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` for the database, and create a Base64 `JWT_SECRET` once. Preserve the current JWT secret when using an existing database; changing it invalidates active access tokens. Keep credentials in the ignored `.env` and out of chat.

The ignored local `.env` is loaded by `scripts/Start-Backend.ps1` and configures nine RSS feeds, GNews, NewsAPI, and six-hour ingestion. The latest run had one GNews HTTP 403; RSS continued successfully. The catalog has articles in all nine categories, including World and General. Keep provider keys server-side. Review each publisher's current feed, excerpt, and image terms before deployment; publisher-supplied image URLs do not by themselves grant reuse rights. See the [final measured status](docs/FINAL_SUBMISSION_CHECK.md).

Start the backend in one terminal:

```powershell
.\scripts\Start-Backend.ps1
```

The script loads the root `.env` into this process and finds cached Maven if Maven is absent from PATH. Flyway currently applies V1 through V8; V5 preserves original article links, V6 prepares optional pgvector storage, V7 persists feed diagnostics, and V8 adds the General category. Hibernate validates the schema. Existing rows and earlier migrations are preserved.

Start the frontend in another terminal:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run dev
```

Open the URL printed by Vite (normally `http://127.0.0.1:5180`). `/api` is proxied to backend port 8080. Use the same hostname throughout login and navigation because refresh cookies belong to that hostname. If Vite chooses another port, add its exact origin to `APP_CORS_ALLOWED_ORIGINS` and restart the backend. To change the backend proxy target, put `VITE_BACKEND_PROXY_TARGET` in `frontend/.env.local`.

The app opens on Login/Register. Register your account, browse ingested stories, save one, reload, and try Pulse Lens or the floating Copilot. Select categories in Preferences for explainable recommendations. Enable article-opening recording in Privacy to populate Analytics and News DNA.

## Diagnose your local database

The configured `pulseai` database on `localhost:5432` is PostgreSQL 18. Flyway schema version 8 is current; the additive migrations preserved the existing account and saved citation. The catalog currently contains 745 articles, 253 source records, and 615 article image URLs. Business, Entertainment, General, Health, Politics, Science, Sports, Technology, and World all have articles. The database does not have pgvector installed, so semantic search stays in its labeled keyword fallback mode. No tables were dropped or truncated. Test the connection with a password prompt:

```powershell
Get-Service postgresql*
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h localhost -p 5432 -U YOUR_DATABASE_USER -d pulseai -W -c 'SELECT current_database(), version();'
```

Use your installed PostgreSQL version and port; this machine also has PostgreSQL 14 and a listener on 5433. If login succeeds but application startup fails, check the four required `.env` values and the Flyway error. The database user needs migration privileges. Never repair checksums by editing applied migrations. Back up existing data before applying a schema update.

With the backend running:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8080/api/v1/categories
Invoke-RestMethod http://localhost:8080/api/v1/sources
Invoke-RestMethod http://localhost:8080/api/v1/news/status
Invoke-RestMethod 'http://localhost:8080/api/v1/articles?page=0&size=12'
```

The liveness endpoint does not claim database readiness; catalog and status requests perform actual database operations. `/api/v1/news/status` reports refresh times, counts, configured providers, schedule, and cache bounds. Ingestion starts on schedule when enabled; administrators can also use `POST /api/v1/admin/ingestion/run`. Registration always grants USER; an operator must provision ADMIN through trusted database administration.

## Storage and freshness

Article metadata expires after 48 hours by default; the local `.env` retains it for 168 hours and caps the catalog at 2,000 rows. Cleanup runs every 15 minutes and during ingestion. Descriptions are plain text, limited to 2,000 characters. Images remain external URLs; no image binaries or full publisher bodies are stored. Up to 500 minimal saved citations per user survive article expiry, retaining the title, original link, publisher, category, and timestamp. Expired citations open the original publisher.

Reading history is off by default, expires with its article or after 30 days, and can be cleared in Privacy. Copilot conversations stay in application memory and disappear when the app session ends or the page reloads. Search queries and assistant responses are not stored by the backend. Session credentials expire after seven days; refresh rotates the hashed opaque token, and logout revokes the session and its JWTs.

RSS freshness follows publishers and the configured six-hour schedule. The GNews Free plan currently documents a 100-request daily quota, 10 articles per request, and a 12-hour delay; the NewsAPI Developer plan is for development and testing. Check current provider terms before using either plan commercially. See the [ingestion guide](docs/architecture/news-ingestion.md).

## Builds and tests

```powershell
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend test
.\scripts\Verify-Postgres.ps1
.\scripts\Verify-Postgres.ps1 -LiveNews -Test LiveNewsSmokeTest
```

`Verify-Postgres.ps1` creates a temporary SCRAM-protected cluster on port 55432, runs tests against real PostgreSQL, and stops only its own server. It does not touch your existing services/data. Override `-PostgresBin`, `-MavenPath`, or `-Port` when needed. Cluster artifacts are ignored under `.runtime`; tests can also use H2 with `mvn test` in `backend`. Docker enables the optional Testcontainers PostgreSQL 17 test. See [verification details](tests/README.md).

The FastAPI service uses the same model name as the backend (`sentence-transformers/all-MiniLM-L6-v2` by default) and exposes `/v1/embeddings`:

```powershell
Set-Location ai-service
if (-not (Test-Path -LiteralPath .venv)) { py -3.10 -m venv .venv }
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest
$env:HF_HOME = Join-Path (Get-Location) '.runtime\huggingface'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --ws none
```

## Optional Compose stack

`docker compose up --build` runs a separate PostgreSQL 17 pgvector database, backend, frontend, and internal AI service. Configure `.env` first. Docker was unavailable on this machine, so container startup was not verified. Existing PostgreSQL occupies port 5432; change the Compose host port before using this alternative. Compose uses its own database and volume, rather than the existing Windows service. Frontend Nginx proxies `/api/` to the backend; secrets are supplied only to server containers.

See [API contracts](docs/api/backend-api.md), [architecture](ARCHITECTURE.md), [security](docs/architecture/security.md), and [roadmap](ROADMAP.md). Production work still includes TLS, authentication abuse controls, managed keys, dependency scanning, backups/restore drills, multi-instance job coordination, and deployment verification.
