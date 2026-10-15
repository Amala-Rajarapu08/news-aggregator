# Verification

## Repeatable checks

Run from the workspace root in PowerShell:

```powershell
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend test
.\scripts\Verify-Postgres.ps1
.\scripts\Verify-Postgres.ps1 -LiveNews -Test LiveNewsSmokeTest
.\ai-service\.venv\Scripts\python.exe -m pytest ai-service/tests
```

The default backend test profile uses H2. `Verify-Postgres.ps1` supplies the PostgreSQL URL, driver, dialect and credentials for the same API/ingestion/security suite. It creates a separate random-password SCRAM database cluster on 127.0.0.1:55432 and stops its server in finally. Existing PostgreSQL services/data are untouched. Override `-PostgresBin`, `-MavenPath`, `-Port`, or `-Test` as needed. PostgreSQL binaries, Java, and Maven are required; no Docker is needed for this path.

`FlywayUpgradeTest` additionally proves V3-to-V4 citation backfill on an existing saved record. `WorkspaceIntegrationTest` proves refresh rotation, logout bearer revocation, preserved archived citations, retention/count cap, protected/source-linked assistant results, and explicit category recommendations. Provider transport tests exercise Retry-After cooldown and streaming size bounds. Personal-data tests check consent and per-user isolation. Fixtures are test-only and never become production output.

The live RSS test is opt-in because external availability/new publication varies. It uses the NASA RSS feed, checks actual inserts/image metadata and repeat-fetch duplicates. Failures must remain failures, not fabricated fixtures. Testcontainers PostgreSQL 17 requires Docker and skips if unavailable. A skip is not a pass.

## Browser verification record

On 2026-10-13, headless Edge/CDP exercised the updated app at desktop 1440x1050 and mobile 390x844 against the configured PostgreSQL database through an isolated Vite 5183 → Spring 8082 preview. All 19 assertions passed, covering unauthenticated redirect, registration, every declared route, Science/publisher filters, keyword search and no-results, the NASA publisher metadata search fallback on the semantic route, article fallback imagery, bookmark/session refresh, Lens, sourced Copilot, profile/preferences persistence, article context, consented analytics/history, mobile overflow, and logout. No JavaScript exceptions or console errors were recorded. The temporary QA account and related rows were removed; the pre-existing account remained. Screenshots and local CDP artifacts are ignored under `.runtime`; the harness remains a local tool rather than a general CI runner.

See [implementation-report.md](../docs/implementation-report.md) for actual counts and remaining unverified checks. CI, Docker runtime, a durable portable browser harness, accessibility auditing beyond focused checks, and model evaluation remain future work.
