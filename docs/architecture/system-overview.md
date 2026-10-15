# System overview

React/Vite uses `/api/v1` through a same-origin proxy to Spring Boot. PostgreSQL stores users, preferences, browser-session hashes, bounded article metadata, sources/categories, saved citations, and optional reading openings. Flyway V1-V4 owns schema; Hibernate validates it. FastAPI currently serves health only.

The authenticated browser restores its short-lived in-memory JWT using a rotating HttpOnly cookie. Catalog discovery/search/category/source/detail, profile/preferences, saves, reading history, and analytics use actual REST contracts. Floating Copilot performs source-linked keyword/excerpt/related retrieval. Lens presents publisher evidence and labelled related candidates; unimplemented ML sections remain compact and honest.

RSS/optional provider adapters normalize metadata and enforce publication-based cache expiry, count caps, uniqueness and request bounds. One status row exposes real refresh timestamps/counters. Images are external URLs with safe fallbacks; full publisher bodies and permanent assistant conversations are absent.

See [architecture decisions](../../ARCHITECTURE.md), [API contracts](../api/backend-api.md), [ingestion](news-ingestion.md), [security](security.md), and the [verified implementation report](../implementation-report.md).
