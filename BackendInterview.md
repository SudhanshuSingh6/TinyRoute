# TinyRoute — Backend Interview Q&A

A deep, codebase-grounded set of interview questions and detailed answers, derived directly from the TinyRoute backend (`com.tinyroute`). Every answer references the actual implementation so you can defend it in an interview.

**Stack:** Java 21 · Spring Boot 3 · PostgreSQL 15 · Redis 7 (Lettuce) · Bucket4j · Flyway · Spring Security · JJWT · Swagger/OpenAPI · Lombok

**Module layout (DDD-ish by feature):** `auth`, `user`, `url`, `redirect`, `analytics`, `ratelimit`, `security`, `infra` (cache + network), `config`, `exception`.

---

## Table of contents

1. [Architecture & High-Level Design](#1-architecture--high-level-design)
2. [The Redirect Hot Path](#2-the-redirect-hot-path)
3. [Caching Layer (Redis)](#3-caching-layer-redis)
4. [Analytics Pipeline (two-stage, async)](#4-analytics-pipeline-two-stage-async)
5. [Authentication & JWT](#5-authentication--jwt)
6. [Refresh Tokens & Rotation](#6-refresh-tokens--rotation)
7. [Spring Security Configuration](#7-spring-security-configuration)
8. [Rate Limiting (Bucket4j)](#8-rate-limiting-bucket4j)
9. [URL Creation, Aliases & Collisions](#9-url-creation-aliases--collisions)
10. [URL Validation & SSRF Protection](#10-url-validation--ssrf-protection)
11. [Persistence, JPA & Concurrency](#11-persistence-jpa--concurrency)
12. [Exception Handling & Error Model](#12-exception-handling--error-model)
13. [Configuration & Operability](#13-configuration--operability)
14. [Testing](#14-testing)
15. [Scaling, Trade-offs & Follow-ups](#15-scaling-trade-offs--follow-ups)
16. [User Profiles & Public Bio Pages](#16-user-profiles--public-bio-pages)
17. [Link Preview & QR Generation](#17-link-preview--qr-generation)
18. [Bean Validation](#18-bean-validation)

---

## 1. Architecture & High-Level Design

### Q1.1 — Give me a high-level overview of the system.
TinyRoute is a URL shortener with per-link analytics, public bio pages, and rate limiting. It's a Spring Boot monolith organized **by feature/domain** rather than by technical layer: each domain (`auth`, `url`, `redirect`, `analytics`, etc.) has its own `controller`/`service`/`repository`/`entity`/`dto` sub-packages.

Two data stores back it:
- **PostgreSQL** — system of record (users, url_mapping, click_event, unique visitors, refresh tokens, edit history).
- **Redis** — three distinct roles: (1) redirect cache, (2) hot-path analytics counters + the raw-event queue, (3) the Bucket4j rate-limit store.

The defining design decision is the **separation of the read-heavy redirect hot path from the write-heavy analytics enrichment**, connected by an asynchronous Redis queue drained by a scheduled background worker.

### Q1.2 — Why a feature-based package structure instead of layered (controller/service/repo at top)?
Feature packaging keeps everything for one capability together, which makes the code easier to navigate and reduces cross-package coupling — a change to "url management" touches one package tree. It also makes module boundaries explicit, so the monolith could later be split into services along those seams (e.g. analytics could become its own service since it already communicates via a Redis queue).

### Q1.3 — Why a monolith and not microservices?
For this scope a monolith is simpler to develop, deploy, and reason about transactionally (e.g. URL edit + edit-history write in one DB transaction). The architecture still keeps the highest-fan-out concern — analytics enrichment — **decoupled via a queue**, which is where you'd extract a service first if traffic demanded it. You get most of the scaling benefit (async write path) without distributed-systems overhead.

---

## 2. The Redirect Hot Path

> Files: `RedirectController`, `RedirectService.getOriginalUrl`

### Q2.1 — Walk me through what happens on `GET /{shortUrl}`.
1. `RedirectController.redirect` receives the path variable.
2. `RedirectService.getOriginalUrl` looks up the mapping: **Redis cache first**, falling back to Postgres (`findByShortUrl`) and populating the cache on a miss.
3. If nothing is found → `null` → controller returns **404**.
4. `resolveStatus()` computes the *current* status from `now`: DISABLED stays DISABLED; past `expiresAt` → EXPIRED; `clickCount >= maxClicks` → CLICK_LIMIT_REACHED; otherwise ACTIVE.
5. If the resolved status differs from the stored one, it persists the new status (`updateStatus`) and **evicts the cache** so the stale entry isn't served again.
6. If not ACTIVE, it returns the mapping; the controller maps DISABLED/EXPIRED/CLICK_LIMIT_REACHED to **410 Gone** with a `RedirectErrorResponse` carrying the enum + a human message.
7. If ACTIVE: resolve client IP, hash it, build a `ClickEventData`, and call `redisAnalyticsService.recordClick(...)` — then the controller sends a **302** with the `Location` header.

### Q2.2 — What is deliberately NOT done on the hot path, and why?
No User-Agent parsing, no geo lookup (external HTTP call to ip-api.com), and **no synchronous DB write of the click**. Those are expensive/slow and would put network latency and write contention on every redirect. The hot path only does cheap Redis operations (cache read + a handful of counter ops + one queue push). All enrichment is deferred to the background worker. This keeps redirect latency low and predictable.

### Q2.3 — Why is `getOriginalUrl` annotated `@Transactional` if you're avoiding DB writes?
The status transition (`updateStatus`) is a conditional DB write, and on a cache miss you read from Postgres; wrapping it keeps that read + conditional update consistent within a single transaction. On the common cache-hit-and-ACTIVE path no write occurs, so the transaction is effectively read-only and cheap. (A reasonable critique: you could make the happy path avoid opening a write transaction entirely — see follow-ups.)

### Q2.4 — The controller's `toSafeRedirectUri` re-validates the URL scheme. Isn't that redundant since URLs are validated at creation?
It's **defense in depth**. URLs are validated and normalized at creation (`UrlValidationService`), but the controller refuses to emit a `Location` header for anything that isn't `http`/`https` with a host. This guards against data that predates validation rules, manual DB edits, or a future bug in the creation path — preventing the redirect endpoint from becoming an open redirect to `javascript:`/`file:` schemes. If it can't build a safe URI it returns 410 with "invalid destination URL".

### Q2.5 — I notice the rate-limit call in `RedirectController` is commented out. What's the implication?
Yes — `applyPublicRateLimit(... REDIRECT ...)` is currently commented out, so redirects are **not** rate limited despite the `REDIRECT 60/min` plan existing and the Swagger docs claiming 429s. That's a known gap: an attacker could hammer redirects. Re-enabling it is a one-liner; the plan and helper already exist. Worth calling out honestly rather than claiming the docs are accurate.

---

## 3. Caching Layer (Redis)

> Files: `RedirectCacheService`, `RedirectCacheEntry`, `CacheConfig`

### Q3.1 — How does the redirect cache work?
`RedirectCacheService` stores a JSON-serialized **`RedirectCacheEntry`** (a projection: id, originalUrl, status, expiresAt, maxClicks, clickCount, totalClickCount) under key `redirect:{shortUrl}` with a TTL of `cache.redirect.ttl-seconds` (default **300s**). On read it deserializes back into a `UrlMapping`. The entry is intentionally a *subset* of the entity — only the fields the redirect path needs — so we don't cache lazy associations like `user`, `clickEvents`, or `editHistory`.

### Q3.2 — How is cache consistency maintained on writes?
Eviction on every mutation. `RedirectService` evicts when a status transition happens. `UrlManagementService` evicts after edit, expiry update, enable, disable, and delete. So any state change that would make a cached entry stale removes it; the next read repopulates from Postgres. It's a **cache-aside (lazy)** strategy with explicit invalidation.

### Q3.3 — What happens if Redis is down?
The cache is best-effort. `get` catches exceptions, evicts, and returns `Optional.empty()` → falls back to Postgres. `put`/`evict` swallow exceptions and just log. So a Redis outage degrades the redirect path to "always hit Postgres" rather than failing requests. Same defensive pattern runs through the analytics Redis helpers.

### Q3.4 — Why a dedicated `cacheObjectMapper` bean?
`CacheConfig` defines a `@Qualifier("cacheObjectMapper")` `ObjectMapper` configured with `JavaTimeModule` (so `LocalDateTime` serializes as ISO strings, not timestamps) and `FAIL_ON_UNKNOWN_PROPERTIES=false` (so adding a field to `RedirectCacheEntry` doesn't break deserialization of older cached JSON). Keeping it separate from the default web `ObjectMapper` means cache serialization rules can evolve independently of the API contract.

### Q3.5 — Could a stale cache entry serve an expired/disabled link?
Briefly, no — because `resolveStatus()` runs on the cached data using the *current* time and the cached `expiresAt`/`clickCount`. Expiry is recomputed every request, so a cached entry whose `expiresAt` has passed is caught and returns 410 (and triggers a status-update + eviction). The cache can lag on `clickCount` (since increments are async), which means the click-limit cutoff is approximate — acceptable for this domain.

---

## 4. Analytics Pipeline (two-stage, async)

> Files: `RedisAnalyticsService`, `RedisAnalyticsHelper`, `RedisAnalyticsEventQueue`, `AnalyticsEventBackgroundWorker`, `UniqueVisitorRegistrationService`, `AnalyticsService`, `RedisAnalyticsConstants`

### Q4.1 — Describe the analytics pipeline end to end.
It's a **two-stage** design:

**Stage 1 (hot path, synchronous, Redis-only):** `RedisAnalyticsService.recordClick` runs a single Lua script (`recordClickAtomic`) that in one round trip:
- `INCR` daily clicks counter,
- `SADD` the IP-hash to today's unique-visitors set,
- `HINCRBY` the hourly hash for the current hour,
- `LPUSH` the serialized raw `ClickEventData` onto `analytics:raw_events`,
- sets a TTL on each key **only if it has none yet** (`TTL < 0`).

Analytics keys get a **48h TTL** (`DAILY_COUNTER_TTL_SECONDS = 2*24*60*60`); the queue gets a 1-day TTL.

**Stage 2 (background, async):** `AnalyticsEventBackgroundWorker.processQueuedClickEvents` runs on `@Scheduled(fixedDelay=5000, initialDelay=2000)`. It `RPOP`s a batch of up to **500** events, enriches each (geo lookup, UA parse, referrer/language normalization), registers unique visitors, updates live aggregate hashes, then bulk-persists `ClickEvent` rows and bumps the per-URL counters.

### Q4.2 — Why use a Lua script for Stage 1 instead of separate Redis commands?
**Atomicity and one network round trip.** All four data structures (counter, set, hash, list) plus their conditional EXPIREs execute as a single atomic unit server-side. Without Lua you'd pay 4–8 round trips per click and risk partial updates if the connection dropped mid-sequence. The `TTL < 0` guard avoids re-issuing `EXPIRE` on every click (which would otherwise keep sliding the window forever and waste a command).

### Q4.3 — Why is the queue an LPUSH/RPOP list, and what does `drainBatch` do?
The queue is a Redis list used as a FIFO: producers `LPUSH` (left), the consumer `RPOP`s from the right. `drainBatch(500)` uses the **count form of `RPOP`** (`rightPop(key, count)`) — a single command that pops up to N elements at once, far cheaper than 500 individual pops. Each popped JSON is deserialized into `ClickEventData`; deserialization failures are logged and skipped rather than failing the whole batch.

### Q4.4 — How are unique visitors counted, and why both Redis and Postgres?
Two layers:
- **Redis (live/today):** `SADD ip_hash` to a per-URL per-day set; `getSetSize` gives today's unique count instantly for the live view.
- **Postgres (durable):** `UniqueVisitorRegistrationService.registerIfFirstVisit` runs a native `INSERT ... ON CONFLICT (url_mapping_id, ip_hash) DO NOTHING` against `url_unique_visitor`, which has a UNIQUE constraint on `(url_mapping_id, ip_hash)`. The insert returns the affected-row count; `> 0` means this was the **first ever** visit from that IP for that URL. That boolean drives whether `clickCount` (unique visitors) gets incremented.

So `url_mapping.click_count` = unique visitors, `url_mapping.total_click_count` = all clicks. The `ON CONFLICT` approach is race-safe at the DB level without needing application locking.

### Q4.5 — Walk through the enrichment in `enrichAndMapEvent`.
Per event it: resolves `clickTime` (or now), ensures an `ipHash` (re-hashes the raw IP if missing), does a **geo lookup** (country/city via ip-api.com, defaulting to "Unknown"), **parses the User-Agent** into device/browser/os, normalizes the referrer ("Direct" if absent) and language (first token before a comma). It calls `registerIfFirstVisit` to get the unique flag, pushes the five **live aggregate dimension hashes** (country/device/browser/os/referrer) via `recordLiveAggregates`, and builds a `ClickEvent` entity. It uses `entityManager.getReference(UrlMapping.class, id)` — a **lazy proxy** — to set the FK without loading the full parent row.

### Q4.6 — Why `entityManager.getReference` instead of `findById`?
`getReference` returns a proxy backed only by the ID; it issues **no SELECT**. Since we only need the foreign-key value to insert a `click_event` row, loading the entire `UrlMapping` would be wasted I/O. This matters because the worker processes up to 500 events per tick.

### Q4.7 — How does the worker handle failures and retries?
The persistence block (saveAll + counter increments) is wrapped in a **3-attempt retry with exponential backoff** (1s → 2s → 4s). Enrichment failures for individual events are caught per-event and skipped so one bad event doesn't poison the batch. If `Thread.sleep` is interrupted it restores the interrupt flag and breaks. The whole method is `@Transactional`, so a successful attempt commits the batch atomically.

### Q4.8 — There's a data-loss risk in the queue drain. Where, and how would you fix it?
`drainBatch` uses `RPOP` which **removes** events from Redis *before* they're persisted. If the worker crashes after popping but before the DB commit, those events are lost. Fixes, in order of robustness:
- Use `LMOVE`/`RPOPLPUSH` into a per-worker **processing list**, delete from it only after commit, and re-claim orphaned processing lists on startup (reliable-queue pattern).
- Or move to Redis **Streams** with consumer groups (`XACK` after commit).
- Or accept the loss as tolerable since analytics are non-critical (the current stance). Worth naming explicitly.

### Q4.9 — How do live aggregates differ from historical analytics?
- **Live** (`GET /api/urls/analytics/{shortUrl}/live`): read straight from Redis hashes/sets/counters for *today* via `getLiveAnalytics`. Fast, eventually-consistent, 48h retention.
- **Historical** (`GET /api/urls/analytics/{shortUrl}`): `AnalyticsService.getAnalytics` queries Postgres `click_event` rows between a date range and the durable unique-visitor count, then `AnalyticsMapper` shapes the response. Durable, exact, supports arbitrary ranges.

### Q4.10 — How are Redis keys structured for analytics?
`RedisAnalyticsConstants` centralizes key builders, all prefixed `analytics:url:{id}:{dimension}:{ISO-date}`, e.g. `analytics:url:42:daily:2026-05-30`, `:unique:`, `:hourly:`, `:country:`, `:device:`, `:browser:`, `:os:`, `:referrer:`. Keying by date makes per-day buckets and TTL-based cleanup trivial, and keeps key cardinality bounded (old days expire automatically).

### Q4.11 — Why a separate `analyticsRedisTemplate` bean?
`RedisAnalyticsConfig` defines a `StringRedisTemplate` with all four serializers set to `StringRedisSerializer` (key/value/hashKey/hashValue). This guarantees that values written by Lua and read back by `getHash`/`getCounter` are plain strings (so `Long.parseLong` works), independent of whatever serialization the default template uses. There's also a `counterRedisTemplate` using `GenericToStringSerializer<Long>`.

---

## 5. Authentication & JWT

> Files: `JwtService`, `JwtAuthenticationFilter`, `AuthService`, `AuthController`, `UserDetailsServiceImpl`, `UserDetailsImpl`

### Q5.1 — How does authentication work overall?
Login (`POST /api/auth/public/login`) authenticates credentials via Spring's `AuthenticationManager` + `DaoAuthenticationProvider` (BCrypt). On success it issues two tokens — a short-lived **access JWT** and an opaque **refresh token** — both delivered as **HttpOnly, SameSite=Lax cookies**. Subsequent requests carry the access cookie; `JwtAuthenticationFilter` validates it and populates the `SecurityContext`. Sessions are **stateless** (`SessionCreationPolicy.STATELESS`).

### Q5.2 — How is the access JWT built and validated?
`JwtService.generateAccessToken` builds an HMAC-SHA256 (`HS256`) JWT with `subject=username`, a `roles` claim (comma-joined authorities), a `type=access` claim, `issuedAt`, and an expiration (`jwt.expiration`). It's signed with a key derived from a Base64-decoded secret. `parseClaims` verifies signature + expiry in one call. `isAccessToken` checks the `type` claim equals `access`.

### Q5.3 — Why the `type=access` claim?
To prevent **token confusion**. The filter only authenticates a token whose `type` is `access`; a refresh-style token (or any other type) is rejected even if validly signed. This stops someone from presenting one token kind where another is expected. (Note: refresh tokens here are actually opaque random strings, not JWTs, so the claim is a belt-and-suspenders measure.)

### Q5.4 — How does `JwtAuthenticationFilter` work? Why `OncePerRequestFilter`?
It extends `OncePerRequestFilter` to guarantee it runs exactly once per request (avoiding double execution across forwards/dispatches). Logic:
1. Resolve the access token (Authorization `Bearer` header first, then `accessToken` cookie — via `resolveAccessToken`).
2. If there's already an authentication in the context, or no token, just continue the chain.
3. Otherwise `parseClaims` (verifies signature + expiry), and **only if `isAccessToken`**, build a `UsernamePasswordAuthenticationToken` from the subject + authorities and set it on the context.
4. Any `JwtException`/`IllegalArgumentException` → clear context and log a warning (request proceeds unauthenticated → eventually 401 at authorization).

### Q5.5 — The filter trusts the roles from the JWT claim and never hits the DB. Trade-off?
**Pro:** zero DB round trip per request — fully stateless, scales horizontally. **Con:** a role change (or a ban) doesn't take effect until the access token expires (15 min). For this app that staleness window is acceptable; if it weren't, you'd shorten access-token lifetime or check a revocation list. The filter builds the principal as a bare username string with authorities — it never loads `UserDetails` on the hot path.

### Q5.6 — How is the JWT secret handled? What validation exists?
`@PostConstruct init()` Base64-decodes `jwt.secret` and enforces a **minimum of 32 bytes (256 bits)** — throwing `IllegalStateException` (fail-fast at startup) if too short or not valid Base64, with a helpful message (`openssl rand -base64 64`). This prevents running with a weak HS256 key. **Caveat:** the secret is currently committed in `application.properties` — in production it must come from an env var / secret manager, which is a clear talking point.

### Q5.7 — Why cookies instead of returning tokens in the JSON body?
HttpOnly cookies aren't readable by JavaScript, which mitigates **XSS token theft**. `SameSite=Lax` mitigates **CSRF** for cross-site POSTs. The `secure` flag is config-driven (`app.cookie.secure`, false in dev, true in prod over HTTPS). The downside is CSRF surface for same-site state-changing requests, partly why SameSite=Lax is used and why this is paired with a SPA on a known origin (CORS `allowCredentials=true` with an explicit origin allowlist).

### Q5.8 — How is the password stored?
`BCryptPasswordEncoder` (bean in `WebSecurityConfig`). Registration encodes with `passwordEncoder.encode(...)`; login delegates verification to `DaoAuthenticationProvider`. BCrypt is adaptive (salted, work-factor tunable), appropriate for password hashing.

### Q5.9 — Walk through registration. How are duplicates handled?
`AuthService.registerUser` normalizes username (trim) and email (trim + lowercase), checks `existsByUsername`/`existsByEmail` up front (fast path → `AlreadyExistsException`), then saves. It **also** wraps the save in a try/catch for `DataIntegrityViolationException` and re-checks uniqueness — this handles the **race** where two concurrent registrations both pass the pre-check; the DB UNIQUE constraints (`idx_username`, `idx_email`) are the real arbiter, and the catch translates the violation into the right 409. That's the correct pattern: check-then-act is racy, so the constraint is the source of truth.

---

## 6. Refresh Tokens & Rotation

> File: `RefreshTokenService`, `AuthController` (`/refresh`, `/logout`)

### Q6.1 — How are refresh tokens generated and stored?
`createRefreshToken` generates **32 random bytes from `SecureRandom`**, hex-encodes them (the raw token sent to the client), then stores only the **SHA-256 hash** of it in `refresh_token.token_hash` (UNIQUE). The raw token never touches the DB. This is the same principle as password hashing: a DB leak doesn't expose usable tokens.

### Q6.2 — Why hash the refresh token but not the access token?
The access token is a signed JWT that's *verified*, not *looked up* — the server holds no per-token record. The refresh token is **opaque and stateful**: it must be looked up to be validated and revoked, so it lives in the DB, and you store a hash so the stored form isn't directly usable if exfiltrated.

### Q6.3 — Explain rotation and reuse detection.
On `/refresh`, `rotateToken(rawToken)`:
1. Hash the incoming token, look it up; not found → 401 `INVALID_REFRESH_TOKEN`.
2. **If it's already revoked → reuse detected.** This means a previously-rotated (and thus stolen) token is being replayed. Response: **revoke ALL active tokens for that user** (`revokeAllActiveForUser`) and throw 401 `REFRESH_TOKEN_REVOKED` — forcing a fresh login and killing the attacker's session.
3. If expired → 401 `REFRESH_TOKEN_EXPIRED`.
4. Otherwise: mark the current token revoked, mint a **new** refresh token, and return a new access token too. One-time-use rotation.

This is the standard **refresh-token rotation with reuse detection** pattern (OWASP-recommended). It limits the value of a stolen refresh token to a single use and detects the theft when the legitimate client next rotates.

### Q6.4 — What does logout do?
`AuthController.logout` reads the refresh cookie, calls `authService.logout` → `revoke(rawToken)` which marks that token revoked (idempotent — uses `ifPresent`), and clears both cookies by setting them with `maxAge=0`. The access JWT itself remains technically valid until expiry (it's stateless), but without the refresh token the session can't be renewed.

### Q6.5 — How long do tokens live?
Access: `jwt.expiration` (the controller sets the access cookie `maxAge` to 15 min; note `application.properties` has `jwt.expiration=172800000` = 48h, so the **cookie lifetime and JWT expiry can diverge** — a discrepancy worth flagging). Refresh: `jwt.refresh-token-expiration=604800000` = **7 days**, matching the refresh cookie `maxAge`.

---

## 7. Spring Security Configuration

> File: `WebSecurityConfig`, `SecurityExceptionHandlers`

### Q7.1 — Walk through the `SecurityFilterChain`.
- **CORS** enabled from `corsConfigurationSource` (explicit origins: `localhost:5173` + `frontend.url`, credentials allowed, standard methods, all headers).
- **CSRF disabled** — justified because the API is stateless/token-based and protected by SameSite cookies + CORS origin allowlist.
- **Exception handling** wired to `SecurityExceptionHandlers` for both the auth entry point (401) and access-denied (403).
- **Stateless** session policy.
- **Authorization rules** (in order): all `OPTIONS` permitted (CORS preflight); `/api/auth/public/**` and `/api/public/**` public; `GET /api/urls/*/preview` and `/api/urls/*/qr` public; `GET /*` public (the redirect endpoint); Swagger paths public; **everything else authenticated**.
- The `JwtAuthenticationFilter` is added **before** `UsernamePasswordAuthenticationFilter`.
- `@EnableMethodSecurity` turns on `@PreAuthorize`.

### Q7.2 — Why is `GET /*` permitted, and is that dangerous?
That rule lets the public redirect endpoint (`GET /{shortUrl}`) work without auth. It's a single-segment matcher (`/*`, not `/**`), so it only matches top-level paths, not nested API routes like `/api/urls`. The risk is that any other top-level GET would also be public — but the controllers under those paths are the redirect and a few intentionally-public endpoints. Defensible, though a more explicit matcher would be safer.

### Q7.3 — How is authorization enforced beyond the URL rules?
Method-level `@PreAuthorize("hasRole('USER')")` on controllers like `UrlCreationController.createShortUrl` and `AnalyticsController`. Ownership is enforced in the **service layer**: `getOwnedUrlOrThrow(shortUrl, userId)` queries by `findByShortUrlAndUserId`/`...AndUserUsername`, so a user can only act on URLs they own — returning `UrlException.notFound()` otherwise (404, not 403, to avoid leaking existence).

### Q7.4 — How are 401 vs 403 returned in a consistent JSON shape?
`SecurityExceptionHandlers` implements both `AuthenticationEntryPoint` (unauthenticated → 401) and `AccessDeniedHandler` (authenticated but forbidden → 403), serializing the same `ApiErrorResponse` shape so security errors look like every other error to the client.

---

## 8. Rate Limiting (Bucket4j)

> Files: `RateLimitHelper`, `RateLimitService`, `RateLimitPlan`, `RateLimitEndpoint`, `BucketConfig`

### Q8.1 — How is rate limiting implemented?
**Bucket4j** with a **distributed Redis backend** (Lettuce `LettuceBasedProxyManager` in `BucketConfig`). Each limit is a token-bucket "plan" (`RateLimitPlan` enum: capacity + refill duration) using `Bandwidth.classic` with **greedy refill**. `RateLimitService.resolveBucket(key, plan)` lazily creates/loads the bucket for a key. Because state lives in Redis, limits are **shared across all app instances** — horizontal scaling doesn't multiply the allowance.

### Q8.2 — What are the limits and keys?
From `RateLimitPlan`: AUTH 10/min, ANALYTICS 20/min, MY_URLS 30/min, URL_MANAGEMENT 20/min, SHORTEN 50/min, REDIRECT 60/min. Keys are `rate_limit:{ENDPOINT}:{identifier}` where identifier is the **user id** for authenticated endpoints and the **IP-hash** for public ones (AUTH login, REDIRECT). Per-endpoint + per-identity buckets keep one noisy endpoint from starving others.

### Q8.3 — How does admin bypass work?
In `getRateLimitResult`, if the user's role is `ROLE_ADMIN`, it short-circuits returning `isAdmin=true` with no probe — and `enforceLimit` skips admins. Admins are never throttled on authenticated endpoints.

### Q8.4 — What happens when the limit is exceeded?
`tryConsumeAndReturnRemaining(1)` returns a `ConsumptionProbe`. For authenticated endpoints, `enforceLimit` throws `RateLimitExceededException` (→ 429 via the global handler, with a `Retry-After` header derived from `getNanosToWaitForRefill`). For public endpoints, `applyPublicRateLimit` returns a `ResponseEntity<ApiErrorResponse>` (429) with `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining` headers. Successful requests also get `X-RateLimit-*` headers via `buildRateLimitHeaders`.

### Q8.5 — What if Redis (the rate-limit store) is unavailable? Fail-open or fail-closed?
**Fail-open.** Both `getRateLimitResult` and `getPublicRateLimit` catch exceptions and return a result with a `null` probe; `enforceLimit` only throws when `probe != null && !isConsumed()`. So if the limiter can't be reached, requests pass. This prioritizes availability over strict enforcement — a deliberate trade-off (you don't want a Redis blip to take down login). The risk: during a Redis outage, abuse is unthrottled.

### Q8.6 — Why token bucket with greedy refill specifically?
Token bucket allows **short bursts** up to capacity while bounding the sustained rate. `Refill.greedy` adds tokens continuously/proportionally over the window (vs. `intervally` which dumps them all at once), giving smoother throttling and a meaningful `Retry-After`.

---

## 9. URL Creation, Aliases & Collisions

> File: `UrlCreationService`

### Q9.1 — How are short codes generated?
8-character codes from a 62-char alphabet (`A–Z a–z 0–9`) using **`SecureRandom`**. 62^8 ≈ 218 trillion combinations — sparse enough that random collisions are rare. `SecureRandom` (not `Random`) makes codes non-guessable, so you can't enumerate other users' links.

### Q9.2 — How are collisions handled?
`createWithGeneratedAlias` retries up to **10 times** (`MAX_ALIAS_ATTEMPTS`): generate a candidate, try to `save`, and on `DataIntegrityViolationException` (the UNIQUE constraint on `short_url`) log and retry with a new code. After 10 failures it throws `ShortUrlGenerationFailedException`. The DB UNIQUE constraint — not an application pre-check — is the authority, which is **race-safe**: two concurrent inserts of the same code can't both succeed.

### Q9.3 — Why catch the DB exception instead of checking `existsByShortUrl` first?
Check-then-insert is a TOCTOU race: another transaction could insert the same code between your check and your save. Relying on the UNIQUE constraint + catching the violation is atomic and correct under concurrency. (For *custom* aliases it does both — a friendly pre-check via `existsByShortUrl` for a clean error, plus the catch as the real guard.)

### Q9.4 — How do custom aliases work, and what's reserved?
If a custom alias is provided (trimmed, non-blank), `createWithCustomAlias` validates it against a **reserved set** {api, admin, login, register, swagger, docs, health, preview, qr} (case-insensitive) to avoid shadowing real routes, checks existence, and saves (catching the violation → `AlreadyExistsException.alias()`).

### Q9.5 — How is `maxClicks` determined?
`determineMaxClicks` switches on role via `RoleLimitConfig`: ADMIN and PREMIUM get the higher limit, USER the lower (per the config — user 10k / premium & admin 100k). It's externalized config, not hardcoded, so limits are tunable without code changes.

### Q9.6 — Can a user create duplicate URLs to the same destination?
No — `existsByOriginalUrlAndUser(normalizedUrl, user)` blocks it with `URL_ALREADY_EXISTS`, nudging reuse of the existing short link. Normalization (below) ensures `http://Example.com` and `http://example.com/` are treated as the same destination. On edit, `existsByOriginalUrlAndUserAndIdNot` does the same excluding the current row.

---

## 10. URL Validation & SSRF Protection

> Files: `UrlValidationService`, `DomainNormalizer`, `DomainBlacklistValidator`

### Q10.1 — Why does a URL shortener need SSRF protection at all?
Because the server (and later, on redirect, users) will fetch/visit destinations. If someone shortens `http://169.254.169.254/` (cloud metadata) or `http://localhost:8080/actuator`, the link could be used to probe internal infrastructure or trick the redirect into pointing at internal services. Validation refuses destinations that resolve to non-public addresses.

### Q10.2 — Walk through `validateAndNormalizeDestinationUrl`.
1. Reject null/blank and URLs **> 2048 chars**.
2. Parse + `normalize()` the URI; reject syntax errors.
3. Require a scheme and host; **scheme must be http/https** (whitelist).
4. **Port whitelist**: only 80, 443, or unspecified (`-1`).
5. Normalize the host (`DomainNormalizer` — IDN/Punycode + lowercasing).
6. Reject if **blacklisted** or an **internal hostname** (`localhost`, `*.local`, `*.internal`, `*.localhost`, `*.localdomain`).
7. **DNS-resolve** the host (`InetAddress.getAllByName`) and reject if **any** resolved address is non-public.
8. Rebuild a normalized URI string and return it.

### Q10.3 — Which IP ranges are blocked, and why "any address"?
`isBlockedAddress` rejects any-local, loopback, link-local (169.254/16 — **cloud metadata**), site-local (RFC1918 private), and multicast. IPv4 also blocks CGN `100.64.0.0/10`, benchmark `198.18.0.0/15`, `0.0.0.0/8`, and `240.0.0.0/4`. IPv6 blocks unique-local `fc00::/7`. It checks **all** addresses from `getAllByName` so a hostname with one public and one private A record can't slip a private target through.

### Q10.4 — What attacks does this miss?
- **DNS rebinding / TOCTOU on the redirect path:** validation resolves DNS at creation, but the *redirect* later uses the stored URL; an attacker controlling DNS could repoint the host to an internal IP after validation. The redirect controller's `toSafeRedirectUri` re-checks the scheme but does **not** re-resolve DNS, so the redirect path is the weak spot. Mitigation would be re-validating at redirect time or pinning the resolved IP.
- **Note the preview path is different:** `UrlPreviewService.getPreview` **re-runs** `validateAndNormalizeDestinationUrl` (which re-resolves DNS) immediately before Jsoup fetches the page, so the server-side scrape is re-validated at fetch time. That's good — but see §17 for why server-side fetching is its own SSRF surface.
Naming these distinctions shows depth.

### Q10.5 — Why normalize (IDN/Punycode + lowercase)?
To **canonicalize** so equivalent hosts compare equal — preventing blacklist bypass via Unicode homoglyphs or mixed case (`ЕxampLE.com` vs `example.com`), and ensuring duplicate detection treats the same destination as the same.

---

## 11. Persistence, JPA & Concurrency

> Files: `UrlMapping`, repositories, migrations, `application.properties`

### Q11.1 — How is optimistic locking used and why?
`UrlMapping` has a `@Version Long version` column. JPA increments it on update and fails with an `OptimisticLockException` if two transactions update the same row concurrently from a stale version. This protects against lost updates on URL state — e.g. concurrent enable/disable/edit. It's optimistic (no DB locks held), which suits a low-contention, high-read workload.

### Q11.2 — Counter updates use atomic SQL UPDATEs, not read-modify-write. Why?
`incrementClickCount`/`incrementTotalClickCount` are `@Modifying` JPQL `UPDATE ... SET count = count + :n WHERE id = :id`. This pushes the arithmetic to the DB in a single statement, which is **atomic and concurrency-safe** without loading the entity or fighting the optimistic-lock version. The background worker batches these per-URL (sums increments in a `HashMap`, then one UPDATE per URL) to minimize statements.

### Q11.3 — Doesn't bumping `clickCount` via raw UPDATE conflict with `@Version`?
A direct `@Modifying` UPDATE bypasses the entity lifecycle, so it doesn't touch/check `version` — which is exactly what you want for high-frequency counters (you don't want every click to risk an optimistic-lock failure). The version guard is reserved for *entity* edits (status, URL, title) that go through `save`.

### Q11.4 — Why `findByShortHash` returns a raw entity but ownership queries return `Optional`?
`findByShortUrl` (hot path) returns `UrlMapping` or null — the caller checks null and returns 404 directly, avoiding `Optional` overhead on the hottest path. Ownership lookups (`findByShortUrlAndUserId`) return `Optional` and use `.orElseThrow(UrlException::notFound)` for clean error flow. Pragmatic split.

### Q11.5 — How is the schema managed? `ddl-auto`?
**Flyway** (`spring.flyway.enabled=true`, `baseline-on-migrate=true`) owns migrations (`V1__initial_schema.sql`, `V2__...`). Hibernate is set to `ddl-auto=validate` — it never alters the schema, only verifies entities match. This is the safe production pattern: migrations are explicit, versioned, and reviewable; Hibernate just guards against drift.

### Q11.6 — What indexes exist and why?
- `url_mapping`: indexes on `user_id` (list-by-user), `status`, `expires_at`, UNIQUE `short_url` (the redirect lookup + collision guard).
- `click_event`: indexes on `click_date`, `(url_mapping_id, ip_hash)`, `url_mapping_id` — supporting range queries and per-URL aggregation.
- `url_unique_visitor`: UNIQUE `(url_mapping_id, ip_hash)` (the ON CONFLICT target) plus single-column indexes.
- `refresh_token`: UNIQUE + index on `token_hash` (lookup), index on `expires_at`, `user_id`.
- `users`: UNIQUE `username`, `email`.

### Q11.7 — How are cascades / deletes handled?
`UrlMapping` has `@OneToMany(cascade=ALL, orphanRemoval=true)` for `clickEvents` and `editHistory`, but `deleteUrl` in `UrlManagementService` **explicitly** deletes children first (`url_edit_history`, `url_unique_visitor`, `click_event`) before the parent — bulk deletes that avoid loading large collections into memory just to cascade them. It then evicts the cache.

### Q11.8 — Are there lazy-loading / N+1 concerns?
Associations are `FetchType.LAZY` (user, clickEvents, editHistory) to avoid loading them on the redirect path. The cache stores a flat projection (`RedirectCacheEntry`), never the entity graph, so deserialization can't trigger lazy loads. Analytics persistence uses `getReference` to avoid loading parents. The main place to watch is mapping lists of entities to DTOs in management endpoints (`getUrlsByUser`), which is a per-user bounded set.

---

## 12. Exception Handling & Error Model

> Files: `GlobalExceptionHandler`, `ApiException`, `ErrorCodes`, `ErrorMessages`, `ApiErrorResponse`, `RedirectErrorResponse`

### Q12.1 — Describe the error model.
Every non-2xx (except redirects) returns a uniform **`ApiErrorResponse`**: `{ status, error, message, path, timestamp }`. `error` is always a **stable constant from `ErrorCodes`** — clients switch on the code, not the human message (which can change/localize). `@RestControllerAdvice GlobalExceptionHandler` centralizes the mapping.

### Q12.2 — How is `ApiException` designed?
It's a `RuntimeException` carrying `httpStatus` + `errorCode` + message (and an optional cause). Domain exceptions (`AlreadyExistsException`, `UrlException`, `InvalidUrlException`, `InvalidDestinationUrlException`, `DomainBlacklistedException`, `RateLimitExceededException`, `ShortUrlGenerationFailedException`) extend it, so the handler can treat them uniformly via one `@ExceptionHandler(ApiException.class)` that reads the carried status/code. New domain errors need no new handler.

### Q12.3 — What specific exceptions are mapped?
Beyond `ApiException`: `BindException`/`MethodArgumentNotValidException` (→ 400 with field-level messages joined), `HttpMessageNotReadableException` (malformed JSON), `DateTimeParseException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `BadCredentialsException` (401), `UsernameNotFoundException` (404), generic `AuthenticationException` (401), `IllegalArgumentException` (400), and a catch-all `Exception` (500, logged at ERROR). `RateLimitExceededException` additionally injects the `Retry-After` header.

### Q12.4 — Why does the redirect endpoint use a different error shape?
Inactive redirects return **410 Gone** with `RedirectErrorResponse { urlStatus, message }`. The frontend needs the **enum** (DISABLED/EXPIRED/CLICK_LIMIT_REACHED) to render the right template/page, which the generic `ApiErrorResponse` doesn't carry. It's a deliberate, documented special case.

### Q12.5 — Why centralize error codes/messages in constants?
Single source of truth: prevents typos and drift between handlers, makes the client contract explicit, and lets messages be edited in one place. The codebase migrated string literals to `ErrorCodes` constants (e.g. in `AuthController`, `RefreshTokenService`) precisely to enforce this.

---

## 13. Configuration & Operability

> Files: `application.properties`, `AsyncConfig`, `RedisConfig`, `SwaggerConfig`, `RoleLimitConfig`, `Dockerfile`, `docker-compose.yml`

### Q13.1 — How is async work configured?
`AsyncConfig` (`@EnableAsync`) defines a `ThreadPoolTaskExecutor`: core 4, max 16, queue 500, thread prefix `async-`, and **`CallerRunsPolicy`** as the rejection handler. CallerRunsPolicy means when the pool + queue are saturated, the submitting thread runs the task itself — applying natural **backpressure** instead of dropping work or growing unbounded.

### Q13.2 — How does the scheduled worker run?
`@Scheduled(fixedDelayString="5000", initialDelayString="2000")` — 2s after startup, then 5s **after the previous run finishes** (fixedDelay, not fixedRate), so a slow batch can't overlap itself. It early-returns if the queue is empty to avoid needless work.

### Q13.3 — How is Redis connected?
`RedisConfig` builds a Lettuce `RedisClient` from host/port with optional SSL and optional username/password (handles password-only vs ACL user+password). A `StatefulRedisConnection<String, byte[]>` with a mixed codec backs Bucket4j (which needs byte values), while analytics use the dedicated string template.

### Q13.4 — How is the client IP resolved, and the dev/prod difference?
`ClientIpService.resolveClientIp` reads `X-Forwarded-For` (first hop) if present, else `getRemoteAddr()`. In **non-prod**, loopback addresses are normalized to the literal `"localhost"` so local testing produces stable analytics; in prod the real IP is kept. IPs are **SHA-256 hashed** before storage (`hashIp`) — the system never persists raw IPs, which is a privacy/GDPR-friendly choice (you can count unique visitors without storing PII).

### Q13.5 — What are the obvious production-hardening gaps?
- `jwt.secret` and the Postgres password are committed in `application.properties` → must be externalized (env/secret manager).
- `app.cookie.secure=false` → must be `true` in prod (HTTPS).
- `app.environment=dev` → set `prod` so IP normalization is off.
- Redirect rate limiting is commented out → re-enable.
- `X-Forwarded-For` is trusted blindly → only trust it behind a known proxy, or it can be spoofed to dodge IP-based limits/analytics.
Calling these out unprompted signals maturity.

### Q13.6 — How is the app documented/run?
Swagger/OpenAPI (`SwaggerConfig`, `/swagger-ui.html`). Dockerized: `Dockerfile` + `docker-compose.yml` bring up API (8080), Postgres (5432), Redis (6379). `server.port=${PORT:8080}` allows platform port injection.

---

## 14. Testing

### Q14.1 — What's the testing strategy?
Layered Spring testing: `@WebMvcTest` for controllers (MockMvc + mocked services), `@DataJpaTest` for repositories (real queries against an embedded/containerized DB), `@SpringBootTest` for integration, and plain JUnit 5 + Mockito for unit tests. This pyramid keeps fast unit/slice tests numerous and full-context tests few.

### Q14.2 — How would you unit-test `RedisAnalyticsService.recordClick` without Redis?
Mock `RedisAnalyticsHelper` and `RedisAnalyticsEventQueue`; verify `recordClickAtomic` is called with the correctly built keys (daily/unique/hourly), the serialized event, and the right TTLs. Because the service swallows exceptions, also assert that a thrown helper exception doesn't propagate (it logs and returns).

### Q14.3 — How would you test rotation/reuse detection?
Mock `RefreshTokenRepository`. Cases: (1) unknown hash → `INVALID_REFRESH_TOKEN`; (2) revoked token → verify `revokeAllActiveForUser` is called and `REFRESH_TOKEN_REVOKED` thrown; (3) expired → `REFRESH_TOKEN_EXPIRED`; (4) happy path → old token marked revoked, new token saved, `RotationResult` returned with a fresh raw token.

### Q14.4 — Known testing gaps in this codebase?
Per `TEST_UPDATE_PLAN.md`: missing service tests for `RefreshTokenService`, `AuthService`, `UserService`, `UrlValidationService`, `RedisAnalyticsService`, `RateLimitHelper`; `UrlCreationServiceTest` is a stub with no `@Test` methods; `GlobalExceptionHandlerTest` covers only a few handlers. The highest-value additions are `UrlValidationService` (security-critical SSRF logic) and the refresh-token rotation flow.

---

## 15. Scaling, Trade-offs & Follow-ups

### Q15.1 — Where does this design scale well, and where does it break first?
**Scales well:** redirects are cache-served and emit only cheap Redis ops; rate limiting and counters are in Redis (shared across instances); the app is stateless so you can run N instances behind a load balancer. **Breaks first:** the background worker — `@Scheduled` runs on **every** instance, so with N instances you get N concurrent drainers competing on one queue (the `RPOP` is atomic so they won't double-process, but it's uncoordinated). Postgres `click_event` insert volume and the per-redirect external geo lookup (in Stage 2) are the next bottlenecks.

### Q15.2 — How would you make the analytics worker safe for multiple instances?
Options: (a) a leader-election / `ShedLock`-style distributed lock so only one instance drains; (b) Redis **Streams + consumer groups** so multiple workers share partitions with acked delivery; (c) extract analytics into its own horizontally-scaled consumer service. Streams also fixes the at-most-once data-loss issue from `RPOP`.

### Q15.3 — How would you reduce DB write load from clicks?
Already batched (≤500 per tick, bulk counter UPDATEs). Further: increase batch size / drain frequency, partition `click_event` by month, pre-aggregate into rollup tables and prune raw events, or write clicks to a columnar store (ClickHouse) for analytics while keeping Postgres for transactional data.

### Q15.4 — The geo lookup is a per-event external HTTP call in Stage 2. Problem?
For a 500-event batch that's up to 500 **sequential** calls to ip-api.com (which is also itself rate-limited and a third-party SPOF). Each call is bounded by the `RestTemplate` timeouts configured in `AppConfig` (**2s connect, 3s read**), so a single hung call can't block forever — but worst-case a full batch of slow lookups could take minutes, capping worker throughput. Improvements: a **local MaxMind GeoIP DB** (no network), an in-memory/Redis cache keyed by IP-hash, or parallelizing/bounding the lookups. It's in the background so it doesn't hurt redirect latency, but it's the throughput bottleneck of Stage 2.

### Q15.5 — If you could change one thing, what would it be?
Strong candidates: (1) make the queue drain reliable (Streams) to stop dropping analytics on crashes; (2) re-enable redirect rate limiting; (3) externalize secrets. The first is the most architecturally interesting because it touches the core async design and the at-most-once vs at-least-once delivery trade-off.

### Q15.6 — Why two stores (Postgres + Redis) instead of one?
They serve different access patterns. Redis gives O(1) atomic counters, sets, TTL-based expiry, and a queue — ideal for the high-frequency, ephemeral hot path and live view. Postgres gives durability, relational integrity (FKs, UNIQUE constraints, transactions), and ad-hoc range queries for historical analytics. Using each for what it's best at is the whole point of the two-stage design; the cost is eventual consistency between live (Redis) and historical (Postgres) numbers.

---

## 16. User Profiles & Public Bio Pages

> Files: `UserService`, `UserController`, `PublicProfileController`, `UserMapper`

### Q16.1 — How do public bio pages work?
`GET /api/public/users/{username}` (public, no auth) returns a `PublicProfileResponse`: the user's username, bio, avatar, and their **publicly visible links**. `UserService.getPublicProfile` loads the user (404 if absent), filters their URLs through `isPubliclyAccessible`, and maps each to a `PublicUrlDTO`. It's a Linktree-style page.

### Q16.2 — What makes a link "publicly accessible"?
`isPubliclyAccessible` (mirrored in both `UserService` and `UrlPreviewService`) requires: status is **ACTIVE**, not past `expiresAt`, and `clickCount < maxClicks`. So disabled/expired/maxed-out links are hidden from the public profile — the owner still sees them in their dashboard, but they don't leak onto the public page.

### Q16.3 — `getPublicProfile` is a GET but it increments `bioPageViews`. Is writing in a GET a problem?
Yes, it's a deliberate side effect: `user.setBioPageViews(getBioPageViews() + 1)` inside a `@Transactional` method (the dirty-checked entity flushes on commit). Pragmatically it's a simple view counter. The concerns to raise in an interview:
- **HTTP semantics:** GET is supposed to be safe/idempotent; a view counter that mutates on GET is a common, accepted pragmatic violation, but it means crawlers/prefetchers inflate counts.
- **Concurrency:** it's a read-modify-write on the entity, so concurrent views can lose increments (last-write-wins). A safer version would be an atomic `UPDATE users SET bio_page_views = bio_page_views + 1` like the click counters use, or a Redis counter flushed periodically (consistent with the analytics design).

### Q16.4 — How is the authenticated profile handled vs the public one?
`UserController` exposes `GET/PUT /api/auth/profile` (authenticated, derived from the principal) returning/updating a `UserProfileDTO`. `updateProfile` trims bio/avatar, treats blank as null, and only sets fields that actually changed (avoiding needless dirty updates). The public controller is a separate, unauthenticated endpoint that exposes only the safe subset of fields — there's a clear split between the private full profile and the public projection.

---

## 17. Link Preview & QR Generation

> Files: `PreviewController`, `UrlPreviewService` (Jsoup + ZXing)

### Q17.1 — How does link preview work?
`GET /api/urls/{shortUrl}/preview` (public) returns OpenGraph-style metadata for the destination. `UrlPreviewService.getPreview`:
1. Resolves the mapping and checks `isPubliclyAccessible` (else 404).
2. **Re-validates** the destination via `validateAndNormalizeDestinationUrl`; if it's now blacklisted/unsafe it logs and returns 404 (so it never scrapes an internal target).
3. **Scrapes** with Jsoup (10s timeout, spoofed desktop User-Agent, `followRedirects(true)`) and extracts `og:title`→`<title>`, `og:description`→`meta[name=description]`, `og:image`, with sensible fallbacks (placeholder image, host as title).
4. Catches each failure type granularly (HTTP status, non-HTML MIME, timeout, IO, generic) and returns whatever partial data it has rather than failing the request.

### Q17.2 — Server-side scraping is itself an SSRF risk. How is it mitigated, and what's left?
**Mitigated:** the re-validation in step 2 re-resolves DNS right before fetching and rejects private/internal targets — better than the redirect path, which doesn't re-resolve.

**Still risky:**
- `followRedirects(true)` means the *destination* can 3xx-redirect Jsoup to an internal address **after** validation passed — the redirect target is not re-validated. This is the classic SSRF-via-redirect bypass.
- There's still a small DNS-rebinding TOCTOU window between the validation resolve and Jsoup's own resolve.
Hardening: disable auto-redirects and validate each hop, or route the fetch through an egress proxy that enforces an allowlist. Strong point to raise.

### Q17.3 — How is the QR code generated?
`generateQr` uses **ZXing** (`QRCodeWriter` → `BitMatrix` → PNG via `MatrixToImageWriter`) to encode the full short URL (`{frontendUrl}/{shortUrl}`, trailing slash stripped) as a **250×250 PNG**, returned with `Content-Type: image/png`. It first checks `isPubliclyAccessible` so QR codes aren't generated for hidden links.

### Q17.4 — Why are preview/QR public but separately whitelisted in security config?
They're explicitly permitted in `WebSecurityConfig` (`GET /api/urls/*/preview` and `/api/urls/*/qr`) because they sit under the otherwise-authenticated `/api/urls/**` tree. They need to be public so unauthenticated visitors (e.g. someone viewing a bio page) can see previews and QR codes, but the surrounding URL-management endpoints stay locked down.

---

## 18. Bean Validation

> Files: `AnalyticsQueryRequest`, request DTOs, `GlobalExceptionHandler`

### Q18.1 — How is request validation done?
Jakarta Bean Validation (`@Valid` on controller params) on request DTOs (`LoginRequest`, `RegisterRequest`, `CreateShortUrlRequest`, etc.). Violations throw `MethodArgumentNotValidException`/`BindException`, which `GlobalExceptionHandler` turns into a 400 with field-level messages joined into one string and error code `VALIDATION_ERROR`.

### Q18.2 — Explain the `@AssertTrue` cross-field validation in `AnalyticsQueryRequest`.
`hasValidDateRange()` is annotated `@AssertTrue(message="endDate must be after startDate")` — Bean Validation treats any boolean `isX()/hasX()` method with a constraint as a derived property. It returns true when either date is null (defaults handled later) or when `endDate.isAfter(startDate)`. This expresses a **relationship between two fields**, which single-field annotations like `@NotNull` can't. Because `AnalyticsQueryRequest` is bound via `@ModelAttribute` (query params), the failure surfaces as a `BindException` → 400.

### Q18.3 — The date range is checked both in the DTO and in `AnalyticsService`. Redundant?
No — they cover different cases. The `@AssertTrue` rejects an explicitly inverted range at the edge. `AnalyticsService.getAnalytics` then applies **defaults** (start = URL creation date, end = now), clamps `end` to now, and re-checks `!end.isAfter(start)` after defaulting — guarding the computed range, not just the submitted one. Defense in depth plus default-handling.

---

### Quick-fire facts to memorize
- Short code: **8 chars**, 62-symbol alphabet, `SecureRandom`, **10** collision retries.
- Redirect cache TTL: **300s**; analytics keys TTL: **48h**; raw-event queue TTL: **1 day**.
- Worker: `fixedDelay=5000`, `initialDelay=2000`, batch **≤500**, **3** retries w/ 1/2/4s backoff.
- Refresh token: 32 random bytes, **SHA-256** stored, **7-day** expiry, rotation + reuse-detection (revoke all).
- Access JWT: **HS256**, `type=access` claim, min secret **32 bytes**.
- Rate limits/min: AUTH 10, ANALYTICS 20, URL_MANAGEMENT 20, MY_URLS 30, SHORTEN 50, REDIRECT 60; admin bypasses; **fail-open**.
- `click_count` = unique visitors; `total_click_count` = all clicks.
- SSRF guard: scheme http/https, ports 80/443/none, blocks private/loopback/link-local/CGN/benchmark/ULA across **all** resolved IPs.
- Role click limits: USER 10k, PREMIUM 100k, ADMIN 100k (`RoleLimitConfig`).
- Geo lookup: ip-api.com via `RestTemplate`, **2s connect / 3s read** timeout; UA parsing via `ua-parser`.
- Preview: Jsoup scrape, **10s** timeout, `followRedirects(true)` (SSRF-via-redirect gap); QR: ZXing **250×250 PNG**.
- Public link visible iff: ACTIVE + not expired + `clickCount < maxClicks`.
- `getPublicProfile` increments `bioPageViews` on a GET (read-modify-write, not atomic).
