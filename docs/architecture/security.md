# Security and privacy

## Implemented

Passwords use BCrypt cost 12; normalized email uniqueness and registration validation are enforced server-side. DTO debug strings redact credentials/tokens. Signing keys are Base64-decoded server secrets with a minimum 32 bytes. HS256 tokens validate issuer, expiry, and a PostgreSQL session ID/user/expiry predicate.

Access tokens stay in browser memory. Random 32-byte refresh credentials are HttpOnly SameSite Strict cookies; only SHA-256 hashes are stored. Refresh rotates under a pessimistic lock, max 20 sessions/account, fixed seven-day expiry. Logout deletes the session and invalidates its issued JWTs. Single-flight refresh prevents parallel rotation within one browser tab. Cross-tab simultaneous refresh may require a fresh sign-in; there is no refresh grace window.

Auth writes reject untrusted supplied Origin and `Sec-Fetch-Site: cross-site`. CORS allows credentials for an explicit origin list. The API otherwise uses bearer authorization and stateless SecurityContext; form login/Basic are disabled. General Spring CSRF middleware is disabled, with explicit Origin/Fetch Metadata checks and SameSite protection at the cookie auth boundary. Deploy behind a same-origin HTTPS proxy and keep `JWT_COOKIE_SECURE=true` in production. Do not replace the exact CORS allowlist with `*`.

Account repositories scope data to the authenticated user. Admin ingestion requires ROLE_ADMIN. Public registration cannot create administrators. Catalog/health are public; unknown API paths are denied. Error responses include correlation IDs, omit internal details, and keep credential failures generic. Passwords, signing keys, provider keys, and private response bodies must never be logged.

Feed URLs are operator-only configuration. HTTP(S) URLs with no user information are accepted; credentials use backend headers, redirects are disabled. XML disables DTDs/external entities. HTML entities are decoded and markup/script/style is removed from plain-text metadata; React escapes text and never renders publisher HTML. External links use safe HTTP(S) validation and noopener/noreferrer; images have no-referrer and publisher fallbacks.

History is off by default, requires a server-validated opt-in, and can be cleared. Openings expire with the article or after 30 days. User saves retain only minimal citation metadata after expiry. Copilot conversations remain in application memory; no private profile goes to an external AI provider. Server search/assistant queries and responses are not persisted.

## Production dependencies

Authentication abuse/rate limiting, email verification/recovery, account export/deletion, audience policy, asymmetric key rotation, comprehensive dependency/container scanning, managed secrets, TLS deployment, backups/restores, and penetration review remain pending. User disabling is checked on login/refresh; operators must also revoke browser sessions to immediately invalidate already-issued tokens. These are explicit production dependencies, not claims of production readiness.

Configured feeds must be trusted operator choices. A strict outbound IP allowlist/DNS-rebinding policy and distributed scheduler/provider quota accounting are future deployment controls. Provider cooldown is process-local and resets at restart. Multiple backend instances require coordinated ingestion to avoid redundant provider requests. Do not log optional user search text when adding observability.
