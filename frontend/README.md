# PulseAI frontend

React 18 / TypeScript / Vite application connected to the Spring Boot product API. Preserve the existing warm neutral, navy, gold, and serif editorial identity. Active routes use real services; legacy demo modules remain dormant.

```powershell
npm.cmd ci
npm.cmd run dev
npm.cmd run build
npm.cmd test
```

Node 22.12+ is required by the installed Vite generation. API base defaults to `/api/v1`; Vite forwards `/api` to port 8080. Put optional public `VITE_BACKEND_PROXY_TARGET` or `VITE_API_BASE_URL` overrides in `frontend/.env.local`, never secrets. Nginx proxies the same API path in Compose. Backend startup/configuration is documented in the [root README](../README.md).

`AuthProvider`, `LibraryProvider`, and `PulseProvider` manage identity, account saves, and session-only contextual research. Tokens remain in memory; the browser restores through a rotating HttpOnly cookie. Account workflows must use the typed REST services; explicit `.ts` imports avoid old `.js` demo service shadowing.

Copilot performs catalog search/publisher-excerpt retrieval; it is not an LLM. Lens related articles are keyword candidates. Events, semantic search, and generated analysis stay visibly pending. No active route may fall back to fixture news or fake analytics.
