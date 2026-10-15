# Backend API contract

Product APIs use `/api/v1`; application liveness uses `/api/health`. Paginated collections return `{items,page,pageSize,totalItems,totalPages}`. Pages are zero-based; size is 1-100. Article metadata uses `id,title,description,url,imageUrl,author,publishedAt,language,source,category`. `source` includes ID, name, domain, and baseUrl. Dates are UTC ISO timestamps. Descriptions are publisher excerpts, never full article bodies or generated summaries.

## Public routes

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/health` | Liveness, environment, timestamp, correlation ID; no readiness claim. |
| GET | `/api/v1/articles` | Publication-ordered catalog; filters `q`, `category`, `sourceId`, `from`, `to`, `page`, `size`. Keyword matching covers title/description. |
| GET | `/api/v1/articles/{id}` | Actual cached metadata; missing/expired ID returns 404. |
| GET | `/api/v1/articles/{id}/related` | `{method,items}`; two shared significant headline words in latest 100 articles; six candidates max. |
| GET | `/api/v1/categories` | Actual seeded category taxonomy. |
| GET | `/api/v1/sources` | Paginated active publishers. |
| GET | `/api/v1/sources/{id}` | Publisher metadata. |
| GET | `/api/v1/topics` | `{topics:[{id,name,category}]}`; operator-created editorial topics, empty until populated. |
| GET | `/api/v1/news/status` | Actual ingestion timestamp/counters/configured provider names/schedule/retention/cache count. No keys or feed URLs returned. |
| GET | `/api/v1/news/coverage` | `{from,midpoint,to,categories}`; each category has total/currentCount/previousCount comparing two 12h windows. |

## Authentication

POST `/auth/register` accepts `{"name":"Pulse Reader","email":"reader@example.test","password":"GoodPassword9!"}`. POST `/auth/login` accepts email/password. Registration returns 201; login returns 200. Both return `{accessToken,tokenType:"Bearer",expiresInSeconds,user}`; user includes `id,name,email,role,createdAt`. BCrypt cost 12; registration validates 12-64 characters and the 72-byte BCrypt limit, mixed-case letters, digit, symbol, name/email, and duplicate normalized email. Registration grants USER only.

POST `/auth/refresh` uses the HttpOnly `pulse_refresh` cookie and returns the same response with a rotated cookie. POST `/auth/logout` revokes that cookie's session, clears the cookie, and returns 204. Browser cookies are SameSite Strict, scoped to `/api/v1/auth`, Secure on production HTTPS, with a fixed seven-day expiry. Their random values are stored only as SHA-256 hashes. Access JWTs are in memory and contain a session ID; every protected request validates its live session, so logout invalidates associated JWTs immediately.

Auth writes require trusted Origin when supplied and reject cross-site Fetch Metadata. Native clients without Origin may call the API. CORS permits credentials only for the exact configured origins. Use a consistent hostname and same-origin reverse proxy in the browser.

## Current-account routes

All paths below start `/api/v1`; send `Authorization: Bearer <accessToken>`. Identity comes from the validated subject, never a caller user ID.

| Method | Path | Behavior |
|---|---|---|
| GET / PUT | `/users/me` | Profile; PUT accepts `{"name":"New name"}`. |
| GET / PUT | `/users/me/preferences` | Briefing frequency, notifications/recommendations flags, historyEnabled, categorySlugs, topicIds, updatedAt. PUT replaces the choices. |
| GET | `/users/me/saved` | Paginated citations with `id,savedAt,archived,article`; archived article has no excerpt/image but retains attribution/link. |
| POST | `/users/me/saved/{articleId}` | Save a cached article; idempotent duplicate; max 500/account. |
| DELETE | `/users/me/saved/{articleKey}` | Remove own citation, including an expired article reference. |
| POST | `/users/me/history` | Record an opening only if historyEnabled; body articleId plus optional openedAt/durationSeconds/completed/scrollPercentage. UI sends zero duration, false completion, no inferred time. |
| GET | `/users/me/history` | Paginated actual retained openings. |
| DELETE | `/users/me/history` | Clear own opening records, 204. |
| GET | `/users/me/recommendations` | Up to 20 recent articles matching explicit preferred categories with an explanation; empty when disabled or no match. |
| GET | `/users/me/analytics` | Retained openings and category/publisher distributions, explicit 30-day upper window; cache expiry may shorten it. |

```json
{
  "briefingFrequency":"DAILY",
  "recommendationsEnabled":true,
  "notificationsEnabled":false,
  "historyEnabled":false,
  "categorySlugs":["science"],
  "topicIds":[]
}
```

Frequency is DAILY/WEEKLY/OFF. Category slugs and topic IDs must exist. Delivery settings are persisted preferences; automated notifications and briefing delivery are not implemented.

## Pulse Copilot

POST `/api/v1/assistant/search` requires authentication and accepts:

```json
{"question":"Show the publisher excerpt","articleId":"<optional-uuid>","pageContext":"/article/<uuid>"}
```

Question is 1-500 characters; pageContext is at most 100. Returns `{mode,message,articles}`. Supported modes:

- `catalog-search`: keyword/category/latest-headline retrieval, eight articles maximum.
- `publisher-excerpt`: actual metadata for the supplied article, explicitly labelled publisher text.
- `related-coverage`: headline candidates with an unverified-event explanation.
- `unavailable`: analysis/why/compare/perspective requests requiring future RAG, with no fabricated answer.

The current adapter uses simple intent rules, not a language model or semantic interpretation. Page context is available for future adapters; article ID drives implemented story retrieval. No question, private profile, response, or conversation is sent to external AI or persisted by this service.

## Administration and errors

POST `/api/v1/admin/ingestion/run` requires ROLE_ADMIN and returns actual provider/fetched/accepted/inserted/duplicate/invalid/failure/elapsed counts. Operator configuration supplies feed URLs; public callers cannot provide arbitrary outbound URLs. Actuator health is secured; unknown routes are denied.

Errors return `{timestamp,status,error,message,path,validationErrors,requestId}` and `X-Request-Id`. Validation 400, no session/bad credentials 401, forbidden 403, missing resources 404, conflicts 409. Unexpected errors omit stack traces and internal details. Login uses a generic incorrect-credentials message. React surfaces API failures with retry/loading states and never substitutes mock news.
