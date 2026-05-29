# TinyRoute — Backend

**Stack:** Java 21 · Spring Boot 3 · PostgreSQL 15 · Redis 7 · Bucket4j · Flyway · Spring Security

Cookie-based JWT auth, async analytics pipeline, Redis redirect cache, distributed rate limiting.

---

## System Architecture

```
                        ┌──────────────────────────────────────────┐
                        │            Spring Boot API               │
                        │                                          │
                        │   JwtAuthenticationFilter                │
                        │   reads accessToken HttpOnly cookie      │
                        │   → validates signature + type claim     │
                        │   → sets SecurityContext                 │
                        └──────────────┬───────────────────────────┘
                                       │
          ┌─────────────┬──────────────┼──────────────┬────────────┐
          │             │              │              │            │
          ▼             ▼              ▼              ▼            ▼
       Auth          URL Mgmt      Redirect       Analytics    Profile
    /api/auth/      /api/urls      /{shortUrl}   /api/urls/   /api/auth/
    public/*        (CRUD,         (hot path,    analytics/   profile
    /api/auth/      history,       rate-limited  {shortUrl}   /api/public/
    profile         expiry)        per IP)       + /live      users/{name}
          │             │              │              │            │
          └─────────────┴──────────────┴──────────────┴────────────┘
                        │                             │
           ┌────────────▼───────────┐    ┌────────────▼────────────┐
           │      PostgreSQL        │    │          Redis           │
           │                        │    │                          │
           │  users                 │    │  redirect:{shortUrl}     │
           │  url_mapping           │    │    TTL configurable      │
           │  click_event           │    │    (default 300 s)       │
           │  url_unique_visitor    │    │                          │
           │  url_edit_history      │    │  analytics:url:{id}:*    │
           │  refresh_token         │    │    TTL 48 h              │
           └────────────────────────┘    │                          │
                                         │  analytics:raw_events    │
                                         │    (event queue, List)   │
                                         │                          │
                                         │  rate_limit:{plan}:{key} │
                                         │    Bucket4j managed      │
                                         └──────────────────────────┘
```

---

## Module Structure

```
com.tinyroute
├── analytics/
│   ├── controller/   AnalyticsController
│   ├── dto/          ClickEventData, LinkAnalyticsResponse, LiveAnalyticsResponse, …
│   ├── entity/       ClickEvent, UrlUniqueVisitor
│   ├── infra/        RedisAnalyticsHelper, RedisAnalyticsEventQueue,
│   │                 RedisAnalyticsConstants, GeoLocationService,
│   │                 UserAgentParsingService
│   ├── mapper/       AnalyticsMapper
│   ├── repository/   ClickEventRepository, UrlUniqueVisitorRepository
│   └── service/      AnalyticsService, RedisAnalyticsService,
│                     AnalyticsEventBackgroundWorker,
│                     UniqueVisitorRegistrationService
├── auth/
│   ├── controller/   AuthController
│   ├── dto/          LoginRequest, RegisterRequest, AuthResponse
│   ├── entity/       RefreshToken
│   ├── repository/   RefreshTokenRepository
│   └── service/      AuthService, RefreshTokenService
├── config/           AppConfig, CacheConfig, AsyncConfig, BucketConfig,
│                     RedisConfig, RoleLimitConfig, SwaggerConfig
├── exception/        ApiException hierarchy, ErrorCodes, ErrorMessages,
│                     GlobalExceptionHandler, ApiErrorResponse
├── infra/
│   ├── cache/        RedirectCacheService, RedirectCacheEntry
│   └── network/      ClientIpService
├── ratelimit/        RateLimitHelper, RateLimitService, RateLimitPlan, RateLimitEndpoint
├── redirect/
│   ├── controller/   RedirectController
│   └── service/      RedirectService
├── security/         WebSecurityConfig, SecurityExceptionHandlers,
│   │                 UserDetailsServiceImpl, UserDetailsImpl
│   └── jwt/          JwtService, JwtAuthenticationFilter
├── url/
│   ├── controller/   UrlCreationController, UrlManagementController, PreviewController
│   ├── dto/          CreateShortUrlRequest, UrlDetailsResponse, UpdateShortUrlRequest, …
│   ├── entity/       UrlMapping (@Version optimistic lock), UrlStatus, UrlEditHistory
│   ├── mapper/       UrlMapper
│   ├── repository/   UrlMappingRepository, UrlEditHistoryRepository
│   ├── service/      UrlCreationService, UrlManagementService,
│   │                 UrlPreviewService, UrlValidationService
│   └── validation/   DomainBlacklistValidator, DomainNormalizer
└── user/
    ├── controller/   UserController, PublicProfileController
    ├── dto/          UserProfileDTO, UpdateProfileRequest, PublicProfileResponse
    ├── entity/       User, Role
    ├── mapper/       UserMapper
    ├── repository/   UserRepository
    └── service/      UserService
```

---

## API Reference

All authenticated endpoints require the `accessToken` HttpOnly cookie.

### Error response — all non-2xx

```json
{
  "status": 404,
  "error": "URL_NOT_FOUND",
  "message": "The requested short URL does not exist.",
  "path": "/api/urls/xyz99",
  "timestamp": "2026-05-29T10:15:30+05:30"
}
```

`error` is always one of the constants in `ErrorCodes.java`.  
Rate-limited responses also carry `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining` headers.

---

### Authentication — `/api/auth`

| Method | Path | Auth | Status | Description |
|--------|------|------|--------|-------------|
| `POST` | `/api/auth/public/register` | ✗ | 201 | Create account |
| `POST` | `/api/auth/public/login` | ✗ | 200 | Authenticate, set cookies |
| `POST` | `/api/auth/public/refresh` | cookie | 200 | Rotate token pair |
| `POST` | `/api/auth/logout` | cookie | 204 | Revoke + clear cookies |
| `GET`  | `/api/auth/profile` | ✓ | 200 | Get own profile |
| `PUT`  | `/api/auth/profile` | ✓ | 200 | Update bio / avatar URL |

**Register / Login request**
```json
{ "username": "alice", "email": "alice@example.com", "password": "secret123" }
{ "username": "alice", "password": "secret123" }
```

On successful login, two `HttpOnly SameSite=Lax` cookies are set:  
`accessToken` (JWT, cookie MaxAge 15 min) and `refreshToken` (raw hex, cookie MaxAge 7 days).

**Profile response**
```json
{
  "username": "alice",
  "email": "alice@example.com",
  "avatarUrl": "https://example.com/avatar.png",
  "bio": "I build things.",
  "bioPageViews": 42
}
```

---

### URL Management — `/api/urls`

All endpoints require `ROLE_USER`.

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST`   | `/api/urls/shorten`                 | 201 | Create short URL |
| `GET`    | `/api/urls`                         | 200 | List own URLs |
| `GET`    | `/api/urls/{shortUrl}`              | 200 | URL details |
| `PUT`    | `/api/urls/{shortUrl}`              | 200 | Update destination + title |
| `PATCH`  | `/api/urls/{shortUrl}/expiry`       | 200 | Update expiry date |
| `PATCH`  | `/api/urls/{shortUrl}/disable`      | 200 | Disable link |
| `PATCH`  | `/api/urls/{shortUrl}/enable`       | 200 | Re-enable link |
| `DELETE` | `/api/urls/{shortUrl}`              | 204 | Delete link + analytics |
| `GET`    | `/api/urls/{shortUrl}/history`      | 200 | Edit history |
| `GET`    | `/api/urls/total-clicks`            | 200 | Aggregate clicks by date |

**Create short URL request** — only `originalUrl` is required
```json
{
  "originalUrl": "https://example.com/very/long/path",
  "customAlias": "launch",
  "title": "Product launch",
  "expiresAt": "2026-12-31T23:59:59"
}
```

**URL details response**
```json
{
  "id": 42,
  "shortUrl": "launch",
  "originalUrl": "https://example.com/very/long/path",
  "title": "Product launch",
  "maxClicks": 10000,
  "clickCount": 128,
  "totalClickCount": 215,
  "status": "ACTIVE",
  "createdAt": "2026-01-15T10:30:00",
  "expiresAt": "2026-12-31T23:59:59"
}
```

`status` values: `ACTIVE` · `DISABLED` · `EXPIRED` · `CLICK_LIMIT_REACHED`  
`maxClicks` is a per-role quota assigned at creation (USER 10,000 · PREMIUM/ADMIN 100,000) — not client-supplied.  
`clickCount` counts unique visitors; `totalClickCount` counts all clicks. A link reaches `CLICK_LIMIT_REACHED` when `clickCount >= maxClicks`.

---

### Redirect + Preview — public

| Method | Path | Auth | Status | Description |
|--------|------|------|--------|-------------|
| `GET` | `/{shortUrl}`                   | ✗ | 302 / 410 | Redirect to destination |
| `GET` | `/api/urls/{shortUrl}/preview`  | ✗ | 200 | OG metadata |
| `GET` | `/api/urls/{shortUrl}/qr`       | ✗ | 200 | QR code (`image/png`) |
| `GET` | `/api/public/users/{username}`  | ✗ | 200 | Public bio page |

Inactive links return `410 Gone` with `{ "status": "DISABLED|EXPIRED|CLICK_LIMIT_REACHED", "message": "…" }`.

---

### Analytics — `/api/urls/analytics`

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `GET` | `/api/urls/analytics/{shortUrl}`       | 200 | Historical (PostgreSQL) |
| `GET` | `/api/urls/analytics/{shortUrl}/live`  | 200 | Today's data (Redis)    |

**Historical query params:** `startDate` · `endDate` (ISO-8601 datetime, both optional)

**Historical response**
```json
{
  "totalClicks": 5432,
  "uniqueClicks": 3210,
  "clicksByTimeBucket": [
    { "bucket": "2026-04-14", "type": "DAY", "count": 320 }
  ],
  "clicksByDimension": [
    { "dimension": "DEVICE",   "key": "Mobile",  "count": 2100 },
    { "dimension": "BROWSER",  "key": "Chrome",  "count": 3000 },
    { "dimension": "COUNTRY",  "key": "IN",      "count": 1800 },
    { "dimension": "REFERRER", "key": "Direct",  "count": 2200 }
  ],
  "peakActivity": { "type": "DAY", "label": "Mon Apr-14", "count": 320 },
  "clickVelocity": { "trend": "UP", "firstHalf": 850, "secondHalf": 1200 }
}
```

Time bucket granularity is auto-selected: HOUR ≤ 48 h · DAY ≤ 60 d · WEEK ≤ 365 d · MONTH > 365 d

**Live response** — Redis counters for today, polled by frontend every 3 s
```json
{
  "todayClicks": 87,
  "todayUniqueVisitors": 61,
  "hourlyClicks": { "09": 12, "10": 31, "11": 44 },
  "countries":   { "IN": 40, "US": 20 },
  "devices":     { "Mobile": 55, "Desktop": 32 },
  "browsers":    { "Chrome": 61, "Safari": 18 },
  "operatingSystems": { "Android": 45, "iOS": 16 },
  "referrers":   { "Direct": 50, "twitter.com": 20 },
  "lastUpdated": "2026-05-29T11:00:00"
}
```

---

## Redirect Hot Path

Every `GET /{shortUrl}` is optimised to touch only Redis on the common case.

```
GET /{shortUrl}
      │
      ▼
applyPublicRateLimit()              Bucket4j · per hashed IP
  └─ 429 + Retry-After if bucket empty
      │
      ▼
RedirectService.getOriginalUrl()
      │
      ├─ 1. RedirectCacheService.get(shortUrl)
      │         HIT  → returns RedirectCacheEntry (projection) ───────┐
      │         MISS → SELECT from url_mapping WHERE short_url = ?    │
      │               → RedirectCacheService.put(entry, TTL 300 s)   │
      │                                                               │
      ▼   ◄──────────────────────────────────────────────────────────┘
 resolveStatus(mapping, now)
      │
      ├─ DISABLED                      ─┐
      ├─ expiresAt < now → EXPIRED     ─┤→ UPDATE status in DB
      ├─ clickCount >= maxClicks       ─┤   evict cache
      │    → CLICK_LIMIT_REACHED       ─┘   return 410 Gone
      │
      └─ ACTIVE → continue
      │
      ▼
ClickEventDataBuilder.buildFromRequest()
  extracts: ip, sha256(ip), userAgent, Referer, Accept-Language, timestamp
  no UA parsing · no geo lookup · no DB write
      │
      ▼
RedisAnalyticsService.recordClick(event)
  INCR  analytics:url:{id}:daily:{date}             TTL 48 h
  SADD  analytics:url:{id}:unique:{date}  <ipHash>  TTL 48 h
  HINCRBY analytics:url:{id}:hourly:{date} {HH} 1  TTL 48 h
  LPUSH analytics:raw_events  <ClickEventData JSON>
      │
      ▼
302 Location: <originalUrl>
```

**What never happens on the hot path:** UA parsing · geo lookup · DB insert · DB update (except status transitions) · any external HTTP call.

---

## URL Lifecycle

```
             POST /api/urls/shorten
                      │
                      ▼
                   ACTIVE ◄──────────────────────────────┐
                      │                                   │
        ┌─────────────┼──────────────┐                    │
        │             │              │                    │
   expiresAt    clickCount >=    user calls           enableUrl()
    < now        maxClicks      disableUrl()         (DISABLED only,
        │             │              │             checks expiry +
        ▼             ▼              ▼             click limit first)
     EXPIRED  CLICK_LIMIT_REACHED  DISABLED ───────────────┘
     [terminal]  [terminal]
```

| Transition | Trigger | Guard |
|---|---|---|
| ACTIVE → DISABLED | `PATCH /disable` | must be ACTIVE |
| DISABLED → ACTIVE | `PATCH /enable` | not expired, click limit not reached |
| ACTIVE → EXPIRED | redirect hot path | `expiresAt < now` |
| ACTIVE → CLICK_LIMIT_REACHED | redirect hot path | `clickCount >= maxClicks` |
| EXPIRED → ACTIVE | `PATCH /expiry` with future date | click limit not reached |

Every status change immediately evicts the redirect cache entry for that `shortUrl`.

---

## Analytics Pipeline

### Stage 1 — hot path (synchronous, 4 Redis ops)

```
RedisAnalyticsService.recordClick(ClickEventData)
  ├─ INCR    analytics:url:{id}:daily:{date}             TTL 48 h
  ├─ SADD    analytics:url:{id}:unique:{date}  <ipHash>  TTL 48 h
  ├─ HINCRBY analytics:url:{id}:hourly:{date}  {HH}  1  TTL 48 h
  └─ LPUSH   analytics:raw_events  <ClickEventData JSON>
```

The SADD deduplicates IP hashes — adding an existing member is a no-op.  
The hourly hash maps `"HH" → count` for the live hourly breakdown chart.

### Stage 2 — background worker (every 5 s)

`AnalyticsEventBackgroundWorker` runs `@Scheduled(fixedDelay = 5000, initialDelay = 2000)`.

```
hasEvents()? → skip if queue is empty
      │
      ▼
drainBatch(500) → RPOP analytics:raw_events  up to 500 items

for each ClickEventData:
  ├─ GeoLocationService.lookup(ip)
  │    GET https://ip-api.com/json/{ip}
  │    timeout: connect 2 s · read 3 s · fallback ("Unknown", "Unknown")
  │
  ├─ UserAgentParsingService.parse(userAgent)
  │    ua-parser → browser · os · deviceType
  │    "Other" device → "Desktop" · "Spider" → "Bot"
  │
  ├─ UniqueVisitorRegistrationService.registerIfFirstVisit(id, ipHash, time)
  │    INSERT INTO url_unique_visitor … ON CONFLICT (url_mapping_id, ip_hash) DO NOTHING
  │    rows inserted > 0 → first visit → isUnique = true
  │
  └─ RedisAnalyticsService.recordLiveAggregates(id, country, device, browser, os, referrer, date)
       HINCRBY analytics:url:{id}:country:{date}  <country>  1  TTL 48 h
       HINCRBY analytics:url:{id}:device:{date}   <device>   1  TTL 48 h
       HINCRBY analytics:url:{id}:browser:{date}  <browser>  1  TTL 48 h
       HINCRBY analytics:url:{id}:os:{date}        <os>      1  TTL 48 h
       HINCRBY analytics:url:{id}:referrer:{date}  <ref>     1  TTL 48 h

clickEventRepository.saveAll(batch)             one transaction, up to 500 rows
urlMappingRepository.incrementTotalClickCount   JPQL bulk UPDATE per url_id
urlMappingRepository.incrementClickCount        JPQL bulk UPDATE, unique visits only

retry: up to 3 attempts · exponential backoff (1 s, then 2 s)
```

### Stage 3 — query paths

```
GET /analytics/{shortUrl}?start=…&end=…          GET /analytics/{shortUrl}/live
  PostgreSQL click_event table                      Redis counters for today only
  date-range filter + dimension aggregation         todayClicks  (daily INCR key)
  auto-selected time bucket granularity             todayUniqueVisitors (SET size)
  velocity: first half vs second half count         hourlyClicks (hourly hash)
                                                    country/device/browser/os/
                                                    referrer maps (dimension hashes)
```

---

## Authentication

```
POST /api/auth/public/login
  │
  ├─ AuthenticationManager.authenticate()
  ├─ generate access token   HMAC-SHA256 · TTL = JWT_EXPIRATION · claim: type=access
  ├─ generate refresh token  32 random bytes → hex string
  │                          SHA-256(hex) stored in refresh_token table
  │
  └─ Set-Cookie: accessToken=<jwt>      HttpOnly SameSite=Lax MaxAge=15min
     Set-Cookie: refreshToken=<rawHex>  HttpOnly SameSite=Lax MaxAge=7days


Every request → JwtAuthenticationFilter
  reads "accessToken" cookie
  validateToken()  → signature + expiry check
  isAccessToken()  → type claim must equal "access" (blocks refresh token misuse)
  extract username + roles → SecurityContext


POST /api/auth/public/refresh
  SHA-256(rawHex from cookie) → look up RefreshToken row by token_hash
  revoked?  → revoke ALL active tokens for this user   ← reuse detection
  expired?  → 401 REFRESH_TOKEN_EXPIRED
  mark old token revoked=true
  issue new access + refresh pair → set new cookies


POST /api/auth/logout
  revoke refresh token hash in DB
  Set-Cookie: accessToken=""  MaxAge=0
  Set-Cookie: refreshToken="" MaxAge=0   → cleared from browser
```

---

## Rate Limiting

Bucket4j token buckets backed by Redis. One bucket per endpoint-group + identity key.

```
rate_limit:{PLAN}:{userId}         ← authenticated endpoints
rate_limit:{PLAN}:{sha256(ip)}     ← public endpoints (login, redirect)
```

| Plan | Capacity | Window | Scope |
|------|----------|--------|-------|
| AUTH | 10 | 1 min | per IP hash |
| REDIRECT | 60 | 1 min | per IP hash |
| SHORTEN | 50 | 1 min | per user ID |
| MY_URLS | 30 | 1 min | per user ID |
| ANALYTICS | 20 | 1 min | per user ID |
| URL_MANAGEMENT | 20 | 1 min | per user ID |

Exceeded → `429 Too Many Requests`  
Headers: `Retry-After: <seconds>` · `X-RateLimit-Limit: <cap>` · `X-RateLimit-Remaining: 0`  
`ROLE_ADMIN` bypasses all authenticated-endpoint limits.

---

## Database Schema

Schema is managed by Flyway. The entity classes are the authoritative source.

```
users
  id  bigint PK
  username  varchar(30) UNIQUE NOT NULL
  email     varchar(100) UNIQUE NOT NULL
  password  varchar(255) NOT NULL          BCrypt
  role      varchar(20) NOT NULL           ROLE_USER | ROLE_PREMIUM | ROLE_ADMIN
  bio       varchar(300)
  avatar_url varchar(500)
  bio_page_views bigint NOT NULL DEFAULT 0

url_mapping
  id          bigint PK
  version     bigint                        @Version optimistic lock
  short_url   varchar(50) UNIQUE NOT NULL
  original_url varchar(2048) NOT NULL
  title       varchar(150)
  status      varchar CHECK (ACTIVE|EXPIRED|CLICK_LIMIT_REACHED|DISABLED)
  click_count       bigint NOT NULL DEFAULT 0    unique visitor count
  total_click_count integer NOT NULL DEFAULT 0   all clicks
  max_clicks        integer NOT NULL             per-role quota (10k / 100k)
  created_at  timestamp NOT NULL
  expires_at  timestamp
  user_id     bigint FK → users

click_event
  id            bigint PK
  click_date    timestamp NOT NULL
  country       varchar(255)
  city          varchar(255)
  browser       varchar(255)
  os            varchar(255)
  device_type   varchar(255)
  referrer      varchar(255)
  language      varchar(255)
  ip_hash       varchar(255)
  url_mapping_id bigint NOT NULL FK → url_mapping

url_unique_visitor
  id             bigint PK
  ip_hash        varchar(64) NOT NULL
  first_seen_at  timestamp NOT NULL
  url_mapping_id bigint NOT NULL FK → url_mapping
  UNIQUE (url_mapping_id, ip_hash)      DB-level deduplication

url_edit_history
  id             bigint PK
  old_url        varchar(255)
  changed_at     timestamp
  url_mapping_id bigint NOT NULL FK → url_mapping

refresh_token
  id          bigint PK
  token_hash  varchar(64) UNIQUE NOT NULL   SHA-256 of raw token
  revoked     boolean NOT NULL
  expires_at  timestamp NOT NULL
  created_at  timestamp NOT NULL
  user_id     bigint NOT NULL FK → users
```

### Indexes

| Index | Type | Serves |
|-------|------|--------|
| `users(email)` | UNIQUE | Login / registration |
| `users(username)` | UNIQUE | Login / registration |
| `url_mapping(short_url)` | UNIQUE | Redirect lookup |
| `url_mapping(user_id)` | B-tree | Dashboard list |
| `url_mapping(status)` | B-tree | Status filter queries |
| `url_mapping(expires_at)` | B-tree | Expiry range checks |
| `click_event(url_mapping_id)` | B-tree | Analytics queries |
| `click_event(click_date)` | B-tree | Date-range filter |
| `click_event(url_mapping_id, ip_hash)` | B-tree | Deduplication lookup |
| `url_edit_history(url_mapping_id)` | B-tree | History list |
| `url_edit_history(changed_at)` | B-tree | History ordering |
| `url_unique_visitor(url_mapping_id, ip_hash)` | UNIQUE | ON CONFLICT deduplication |
| `url_unique_visitor(url_mapping_id)` | B-tree | Count unique per link |
| `url_unique_visitor(ip_hash)` | B-tree | Cross-link lookup |
| `refresh_token(token_hash)` | UNIQUE | Token lookup on every refresh |
| `refresh_token(user_id)` | B-tree | Revoke-all on reuse detection |
| `refresh_token(expires_at)` | B-tree | Expired token cleanup |

### Concurrency

`UrlMapping.version` (`@Version`) prevents lost updates under concurrent edits.

`click_count` and `total_click_count` are updated via JPQL bulk UPDATE — never read-modify-write:

```java
@Modifying
@Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + :n WHERE u.id = :id")
int incrementClickCount(Long id, Long n);

@Modifying
@Query("UPDATE UrlMapping u SET u.totalClickCount = u.totalClickCount + :n WHERE u.id = :id")
int incrementTotalClickCount(Long id, Long n);
```

---

## Redis Key Schema

| Key | Type | TTL | Written by | Purpose |
|-----|------|-----|------------|---------|
| `redirect:{shortUrl}` | String (JSON) | 300 s (configurable) | `RedirectCacheService` | Redirect cache (stores `RedirectCacheEntry` projection) |
| `analytics:url:{id}:daily:{date}` | String (counter) | 48 h | Stage 1 hot path | Total clicks today |
| `analytics:url:{id}:unique:{date}` | Set | 48 h | Stage 1 hot path | Unique IP hashes today |
| `analytics:url:{id}:hourly:{date}` | Hash (`HH` → count) | 48 h | Stage 1 hot path | Hourly click breakdown |
| `analytics:url:{id}:country:{date}` | Hash (name → count) | 48 h | Stage 2 worker | Country breakdown |
| `analytics:url:{id}:device:{date}` | Hash (name → count) | 48 h | Stage 2 worker | Device type breakdown |
| `analytics:url:{id}:browser:{date}` | Hash (name → count) | 48 h | Stage 2 worker | Browser breakdown |
| `analytics:url:{id}:os:{date}` | Hash (name → count) | 48 h | Stage 2 worker | OS breakdown |
| `analytics:url:{id}:referrer:{date}` | Hash (name → count) | 48 h | Stage 2 worker | Referrer breakdown |
| `analytics:raw_events` | List (LPUSH/RPOP) | none | Stage 1 hot path | Unprocessed click queue |
| `rate_limit:{plan}:{key}` | String (Bucket4j) | bucket TTL | `RateLimitService` | Per-endpoint token bucket |

`{date}` is ISO local date (`yyyy-MM-dd`). All 48 h TTLs come from `RedisAnalyticsConstants.DAILY_COUNTER_TTL_SECONDS = 2 * 24 * 60 * 60`.

---

## Security

### Cookie security
`HttpOnly SameSite=Lax` — JavaScript cannot read tokens; cross-site sub-resource requests are rejected.  
Set `app.cookie.secure=true` + `SameSite=Strict` in production (HTTPS only).

### CSRF
Spring CSRF disabled; `SameSite=Lax` is the primary defence. For stricter production: enable `CookieCsrfTokenRepository` or switch to `SameSite=Strict`.

### Password hashing
BCrypt, Spring Security default cost (10 rounds).

### Refresh token security
Raw tokens are never stored — only SHA-256 hashes are persisted.  
Rotation on every refresh: old hash is marked revoked, new pair issued.  
Reuse detection: a revoked token being presented triggers revocation of all active tokens for that user.

### URL validation
`UrlValidationService` runs every destination URL through:
1. Scheme whitelist — `http` / `https` only
2. Port whitelist — 80, 443, or no explicit port
3. IDN normalisation (`DomainNormalizer`)
4. Static domain blacklist (`DomainBlacklistValidator`)
5. DNS resolution — rejects RFC 1918, loopback, link-local, CGN (100.64/10), benchmark (198.18/15)

---

## Testing

| Layer | Framework | Covers |
|---|---|---|
| Unit | JUnit 5 + Mockito | Redirect logic, analytics aggregation, URL validation, rate limiting, concurrency rules |
| Controller | `@WebMvcTest` | HTTP contracts, status codes, validation, exception handling, response structure |
| Repository | `@DataJpaTest` | JPQL queries, constraints, cascade rules, optimistic locking, bulk updates |
| Integration | `@SpringBootTest` | Full redirect flow, concurrent redirects, click limits, unique visitor deduplication |

---

## Local Development

### Docker (recommended)

```bash
cd backend
docker compose up --build
```

| Service | Port |
|---------|------|
| Spring Boot | 8080 |
| PostgreSQL | 5432 |
| Redis | 6379 |

Swagger UI: `http://localhost:8080/swagger-ui.html`

### Manual

Requirements: Java 21 · PostgreSQL 15 · Redis 7

```bash
createdb tinyroute
./mvnw spring-boot:run
```

---

## Environment Variables

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `JWT_SECRET` | HMAC-SHA256 signing secret |
| `JWT_EXPIRATION` | Access token TTL (ms) |
| `JWT_REFRESH_TOKEN_EXPIRATION` | Refresh token TTL (ms) |
| `FRONTEND_URL` | Allowed CORS origin |
| `SPRING_DATA_REDIS_HOST` | Redis host |
| `SPRING_DATA_REDIS_PORT` | Redis port |
| `APP_COOKIE_SECURE` | `true` in production (requires HTTPS) |
| `CACHE_REDIRECT_TTL_SECONDS` | Redirect cache TTL, default `300` |
