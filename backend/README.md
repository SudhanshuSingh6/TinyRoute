# TinyRoute — Backend

Spring Boot 3 / Java 21. Cookie-based JWT auth, async analytics pipeline, Redis redirect cache, distributed rate limiting, PostgreSQL persistence.

---

## Full backend architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Spring Boot API                            │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │              JwtAuthenticationFilter                        │    │
│  │   reads "accessToken" HttpOnly cookie → sets SecurityContext│    │
│  └───────────────────────────┬─────────────────────────────────┘    │
│                              │                                      │
│   ┌──────────┬───────────────┼──────────────┬──────────────────┐    │
│   │          │               │              │                  │    │
│   ▼          ▼               ▼              ▼                  ▼    │
│ Auth      URL Mgmt       Redirect       Analytics           Public  │
│ /api/auth /api/urls     /{shortUrl}    /analytics/**       /api/    │
│           (CRUD,        (hot path)     (live + hist.)      public/  │
│           history,                                         qr,      │
│           expiry)                                          preview) │
│   │          │               │              │                  │    │
└───┼──────────┼───────────────┼──────────────┼──────────────────┼────┘
    │          │               │              │                  │
    ▼          ▼               ▼              ▼                  ▼
┌───────────────────────┐  ┌────────────────────────────────────────┐
│     PostgreSQL        │  │                 Redis                  │
│                       │  │                                        │
│  users                │  │  redirect:{shortUrl}     5 min TTL     │
│  url_mapping          │  │  analytics:url:{id}:*    30 day TTL    │
│  click_event          │  │  analytics:raw_events    1 day TTL     │
│  url_unique_visitor   │  │  rate_limit:{plan}:{key} Bucket4j TTL  │
│  url_edit_history     │  │                                        │
│  refresh_token        │  └────────────────────────────────────────┘
└───────────────────────┘
```

---

## Redirect hot path

Every `GET /{shortUrl}` is optimised to touch only Redis on the common case.

```
GET /{shortUrl}
       │
       ▼
RateLimitHelper.applyPublicRateLimit()        ← Bucket4j, per hashed IP
  └─ 429 + Retry-After if bucket empty
       │
       ▼
UrlRedirectService.getOriginalUrl()
       │
       ├─ 1. RedirectCacheService.get(shortUrl)
       │         HIT  ─────────────────────────────────────────┐
       │         MISS → SELECT * FROM url_mapping              │
       │                 WHERE short_url = ?                   │
       │               → RedirectCacheService.put(shortUrl, m) │
       │                                                       │
       ▼    ◄──────────────────────────────────────────────────┘
  resolveStatus(mapping, now)
       │
       ├─ status == DISABLED           ─┐
       ├─ expiresAt < now              ─┤→ UPDATE status in DB
       │    → mark EXPIRED              │  evict cache
       ├─ dbClicks + redisUnique        │  return mapping (non-ACTIVE)
       │    >= maxClicks               ─┘
       │    → mark CLICK_LIMIT_REACHED
       │
       └─ ACTIVE → continue
       │
       ▼
ClickEventDataBuilder.buildFromRequest()
  extracts: ip, sha256(ip), userAgent, Referer, Accept-Language, now
  no UA parsing · no geo lookup · no DB write
       │
       ▼
RedisAnalyticsService.recordClick(event)
  INCR  analytics:global:clicks
  INCR  analytics:url:{id}:total
  INCR  analytics:url:{id}:daily:{date}          TTL 30 days
  SADD  analytics:url:{id}:unique:{date}  ipHash  TTL 30 days
  LPUSH analytics:raw_events  <JSON>             TTL 1 day
       │
       ▼
302 Location: <originalUrl>
```

**What never happens on the hot path:** UA parsing, geo lookup, DB insert, DB update (except status transitions), any external HTTP call.

**Inactive link response:** `410 Gone` with body `{ status, message }` — the frontend can render a human-readable error.

---

## URL lifecycle

```
                    POST /api/urls/shorten
                           │
                           ▼
                        ACTIVE ◄─────────────────────────────────┐
                           │                                     │
           ┌───────────────┼─────────────────┐                   │
           │               │                 │                   │
      expiresAt      clickCount         user calls               │
       < now         >= maxClicks      disableUrl()         enableUrl()
           │               │                 │            (DISABLED only,
           ▼               ▼                 ▼           checks expiry +
        EXPIRED   CLICK_LIMIT_REACHED    DISABLED ───────► click limit
                                                          before allowing)
           │               │
     [terminal]      [terminal]
  cannot re-enable  cannot re-enable
```

**Status change rules**

| Transition | Allowed | Guard |
|---|---|---|
| ACTIVE → DISABLED | `disableUrl()` | must be ACTIVE |
| DISABLED → ACTIVE | `enableUrl()` | not expired, not at click limit |
| ACTIVE → EXPIRED | redirect hot path or expiry check | `expiresAt < now` |
| ACTIVE → CLICK_LIMIT_REACHED | redirect hot path | `dbClicks + redisUnique >= maxClicks` |
| EXPIRED → ACTIVE | `updateExpiry()` with future date | click limit not yet reached |

Every status change immediately evicts the redirect cache entry for that `shortUrl`.

---

## Analytics pipeline

### Stage 1 — hot path (synchronous)

Five Redis operations per redirect, no blocking IO.

```
RedisAnalyticsService.recordClick(ClickEventData)
  │
  ├─ INCR analytics:global:clicks                       no TTL
  ├─ INCR analytics:url:{id}:total                      no TTL
  ├─ INCR analytics:url:{id}:daily:{yyyy-MM-dd}         30-day TTL
  ├─ SADD analytics:url:{id}:unique:{date}  <ipHash>    30-day TTL
  └─ LPUSH analytics:raw_events  <ClickEventData JSON>  1-day TTL
```

The Redis Set in step 4 deduplicates IPs automatically — SADD on a member that already exists is a no-op.

### Stage 2 — background worker (every 5 s)

`AnalyticsEventBackgroundWorker` runs `@Scheduled(fixedDelay=5000, initialDelay=2000)`.

```
hasEvents()? → skip if queue empty
      │
      ▼
drainBatch(500)  →  RPOP analytics:raw_events 500  (single command)
      │
      ▼
for each ClickEventData:
  │
  ├─ GeoLocationService.lookup(ip)
  │    GET https://ip-api.com/json/{ip}?fields=country,city,status
  │    connect 2 s · read 3 s · returns ("Unknown","Unknown") on any error
  │
  ├─ UserAgentParsingService.parse(userAgent)
  │    ua-parser → browser, os, deviceType
  │    "Other" device → "Desktop" · "Spider" → "Bot"
  │
  ├─ uniqueVisitorRegistrationService.registerIfFirstVisit(id, ipHash, clickTime)
  │    INSERT INTO url_unique_visitor ... ON CONFLICT (url_mapping_id, ip_hash) DO NOTHING
  │    returned rows > 0  → first visit → incrementClickCount(id)   [JPQL bulk UPDATE]
  │    returned rows == 0 → repeat visit → skip
  │
  ├─ RedisAnalyticsService.recordEnrichedAggregates(id, browser, country, device, date)
  │    INCR analytics:url:{id}:browser:{browser}:{date}   30-day TTL
  │    INCR analytics:url:{id}:country:{country}:{date}   30-day TTL
  │    INCR analytics:url:{id}:device:{device}:{date}     30-day TTL
  │
  └─ build ClickEvent entity

clickEventRepository.saveAll(batch)   ← one transaction, up to 500 rows
retry on failure: attempts 1/2/3 with 1 s / 2 s / 4 s backoff
```

### Stage 3 — query paths

```
┌──────────────────────────────────┐   ┌────────────────────────────────────────┐
│ GET /analytics/live/{shortUrl}   │   │ GET /analytics/{shortUrl}              │
│   → Redis only                   │   │    ?startDate=…&endDate=…              │
│                                  │   │   → PostgreSQL click_event table       │
│ LiveAnalyticsResponse            │   │                                        │
│   totalClicks                    │   │ LinkAnalyticsResponse                  │
│   todayClicks                    │   │   totalClicks, uniqueClicks            │
│   uniqueVisitorsToday            │   │   clicksByDimension[]                  │
│                                  │   │     COUNTRY/DEVICE/BROWSER/OS/REFERRER │
│ Polling: frontend every 3 s      │   │   clicksByTimeBucket[]                 │
│                                  │   │     HOUR  range ≤ 48 h                │
│                                  │   │     DAY   range ≤ 60 d                │
│                                  │   │     WEEK  range ≤ 365 d               │
│                                  │   │     MONTH range > 365 d               │
│                                  │   │   peakActivity { type, label, count } │
│                                  │   │   clickVelocity { trend: UP|DOWN|STABLE│
│                                  │   │     firstHalf, secondHalf }           │
└──────────────────────────────────┘   └────────────────────────────────────────┘
```

---

## Authentication

```
POST /api/auth/public/login  { username, password }
  │
  ├─ AuthenticationManager.authenticate()
  ├─ generate access token   HMAC-SHA256 · 15 min · claim type=access
  ├─ generate refresh token  32 random bytes → hex → SHA-256 stored in DB
  │
  └─ Set-Cookie: accessToken=<jwt>      HttpOnly SameSite=Lax MaxAge=15min
     Set-Cookie: refreshToken=<rawHex>  HttpOnly SameSite=Lax MaxAge=7days


Every request → JwtAuthenticationFilter
  reads "accessToken" cookie
  validateToken()  → signature + expiry
  isAccessToken()  → type claim must equal "access" (blocks refresh token misuse)
  extract username + roles → SecurityContext


POST /api/auth/public/refresh   cookie: refreshToken=<rawHex>
  SHA-256(rawHex) → look up RefreshToken in DB
  revoked? → revoke ALL tokens for this user   ← reuse-detection
  expired? → 401
  mark old revoked=true · issue new access + refresh pair
  Set-Cookie both cookies


POST /api/auth/public/logout
  revoke refresh token hash in DB
  Set-Cookie: accessToken=""  MaxAge=0
  Set-Cookie: refreshToken="" MaxAge=0
```

---

## Rate limiting

Bucket4j token buckets in Redis. One bucket per endpoint-group + user-or-IP key.

```
rate_limit:{PLAN}:{userId}         ← authenticated endpoints
rate_limit:{PLAN}:{sha256(ip)}     ← public endpoints (login, redirect)
```

| Plan | Cap | Scope |
|------|-----|-------|
| AUTH | 10 / min | per IP hash |
| SHORTEN | 50 / min | per user ID |
| ANALYTICS | 20 / min | per user ID |
| MY_URLS | 30 / min | per user ID |
| URL_MANAGEMENT | 20 / min | per user ID |
| REDIRECT | 60 / min | per IP hash |

Exceeded → `429 Too Many Requests` + `Retry-After: <seconds>` + `X-RateLimit-Remaining: 0`.
`ROLE_ADMIN` bypasses all authenticated-endpoint limits.

---

## Database design

### Entities

```sql
users
  id · username UNIQUE · email UNIQUE · password (BCrypt)
  role (ROLE_USER|ROLE_PREMIUM|ROLE_ADMIN)
  bio · avatarUrl · bioPageViews

url_mapping
  id · short_url UNIQUE · original_url · title
  status CHECK(ACTIVE|EXPIRED|CLICK_LIMIT_REACHED|DISABLED)
  click_count · max_clicks · expires_at · is_public
  created_date · version   ← @Version optimistic lock
  user_id FK users

click_event
  id · click_date · country · city
  browser · os · device_type · referrer · language · ip_hash
  url_mapping_id FK url_mapping

url_unique_visitor
  id · ip_hash · first_seen_at
  url_mapping_id FK url_mapping
  UNIQUE (url_mapping_id, ip_hash)    ← deduplication at DB level

url_edit_history
  id · old_url · changed_at
  url_mapping_id FK url_mapping

refresh_token
  id · token_hash UNIQUE · revoked · expires_at · created_at
  user_id FK users
```

### Indexing strategy

| Index | Query it serves |
|-------|----------------|
| `url_mapping(user_id)` | list own URLs (dashboard) |
| `url_mapping(status)` | status filter queries |
| `url_mapping(expires_at)` | expiry range checks |
| `click_event(url_mapping_id)` | analytics date-range queries |
| `click_event(click_date)` | time-range filter |
| `click_event(url_mapping_id, ip_hash)` | composite lookup |
| `url_unique_visitor(url_mapping_id)` | count unique visitors |
| `url_unique_visitor(ip_hash)` | cross-URL lookup |
| `refresh_token(token_hash)` | token lookup on every refresh |
| `refresh_token(user_id)` | revoke-all on reuse detection |

### Optimistic locking

`UrlMapping.version` (`@Version`) prevents lost updates when two requests try to modify the same mapping concurrently. One wins, the other gets `ObjectOptimisticLockingFailureException` and retries.

`clickCount` is updated via a JPQL bulk UPDATE — never read-modify-write:

```java
@Modifying
@Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1 WHERE u.id = :id")
int incrementClickCount(Long id);
```

---

## Security

### HttpOnly cookies

Tokens are `HttpOnly SameSite=Lax`. JavaScript cannot read them, blocking XSS-based token theft. `SameSite=Lax` rejects cross-site sub-resource requests.
Set `Secure=true` + `SameSite=Strict` in production.

### CSRF

Spring CSRF protection is disabled; `SameSite=Lax` is the primary defence. For stricter production config: enable `CookieCsrfTokenRepository` (double-submit) or switch to `SameSite=Strict`.

### Password hashing

BCrypt, Spring Security default cost (10 rounds).

### URL validation

`UrlValidationService` runs every destination URL through:
1. Scheme whitelist — `http` / `https` only
2. Port whitelist — 80, 443, or default
3. IDN normalisation via `DomainNormalizer`
4. Static domain blacklist (`DomainBlacklistValidator`)
5. Live DNS resolution → reject RFC 1918, loopback, link-local, CGN (100.64/10), benchmark (198.18/15)

### Refresh token security

- Raw tokens are never stored; only SHA-256 hashes are persisted.
- Rotation on every refresh — old hash marked revoked immediately.
- Reuse detection: revoked token presented → all active tokens for that user revoked at once.

---

## Scalability notes

**Redis absorbs redirect read load.** A popular link with a warm cache never hits PostgreSQL. The 5-minute TTL is configurable via `cache.redirect.ttl-seconds`.

**Analytics writes are deferred and batched.** At 200 redirects/s the worker sees ~1000 events per 5-second window and inserts them in a single transaction — one DB round-trip instead of 1000.

**`clickCount` increment is a single SQL UPDATE**, not a read-modify-write. Safe under high concurrency.

**Stateless auth.** Any backend instance can verify a JWT independently. No sticky sessions, no session store.

**Geo and UA parsing are never on the redirect path.** Latency impact is zero.

## Unit Tests

Framework:

```text
Mockito + JUnit 5
```

Focus areas:

* redirect logic
* analytics aggregation
* validation
* concurrency rules
* rate limiting

---

## Controller Tests

Framework:

```text
@WebMvcTest
```

Verifies:

* HTTP contracts
* validation
* status codes
* exception handling
* response structure

---

## Repository Tests

Framework:

```text
@DataJpaTest
```

Verifies:

* JPQL queries
* constraints
* cascade rules
* optimistic locking
* bulk updates

---

## Integration Tests

Framework:

```text
@SpringBootTest
```

Verifies:

* redirect flows
* concurrent redirects
* click limits
* unique visitor handling
* full service integration

---

# Local Development

## Docker Setup

```bash
cd backend
docker compose up --build
```

Services:

| Service         | Port |
| --------------- | ---- |
| Spring Boot API | 8080 |
| PostgreSQL      | 5432 |
| Redis           | 6379 |

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

---

## Manual Setup

Requirements:

* Java 21
* PostgreSQL 15
* Redis 7

```bash
createdb tinyroute
./mvnw spring-boot:run
```

---

# Environment Variables

| Variable                     | Purpose                 |
| ---------------------------- | ----------------------- |
| `SPRING_DATASOURCE_URL`      | PostgreSQL connection   |
| `SPRING_DATASOURCE_USERNAME` | Database username       |
| `SPRING_DATASOURCE_PASSWORD` | Database password       |
| `JWT_SECRET`                 | JWT signing secret      |
| `JWT_EXPIRATION`             | Access token expiration |
| `FRONTEND_URL`               | Allowed CORS origin     |
| `SPRING_DATA_REDIS_HOST`     | Redis host              |
| `SPRING_DATA_REDIS_PORT`     | Redis port              |

---

# API Overview

## Authentication

| Method | Endpoint                    | Description                            |
| ------ | --------------------------- | -------------------------------------- |
| `POST` | `/api/auth/public/register` | Register a new account                 |
| `POST` | `/api/auth/public/login`    | Authenticate user and issue cookies    |
| `POST` | `/api/auth/public/refresh`  | Rotate refresh token pair              |
| `POST` | `/api/auth/logout`          | Revoke refresh token and clear cookies |

---

## URL Management

| Method   | Endpoint                       | Description                         |
| -------- | ------------------------------ | ----------------------------------- |
| `POST`   | `/api/urls/shorten`            | Create short URL                    |
| `GET`    | `/api/urls`                    | Get all URLs for authenticated user |
| `GET`    | `/api/urls/{shortUrl}`         | Get URL details                     |
| `PUT`    | `/api/urls/{shortUrl}`         | Update URL metadata                 |
| `PATCH`  | `/api/urls/{shortUrl}/disable` | Disable URL                         |
| `PATCH`  | `/api/urls/{shortUrl}/enable`  | Enable URL                          |
| `DELETE` | `/api/urls/{shortUrl}`         | Delete URL                          |

---

## Redirect + Public Endpoints

| Method | Endpoint                       | Description                      |
| ------ | ------------------------------ | -------------------------------- |
| `GET`  | `/{shortUrl}`                  | Redirect to original destination |
| `GET`  | `/api/urls/{shortUrl}/preview` | Generate metadata preview        |
| `GET`  | `/api/urls/{shortUrl}/qr`      | Generate QR code                 |
| `GET`  | `/api/public/users/{username}` | Public bio page                  |

---

## Analytics

| Method | Endpoint                                    | Description             |
| ------ | ------------------------------------------- | ----------------------- |
| `GET`  | `/api/urls/analytics/live/{shortUrl}`       | Live Redis analytics    |
| `GET`  | `/api/urls/analytics/{shortUrl}`            | Historical analytics    |
| `GET`  | `/api/urls/analytics/{shortUrl}/summary`    | Analytics summary       |
| `GET`  | `/api/urls/analytics/{shortUrl}/timeseries` | Time-bucketed analytics |

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

---

# Performance Characteristics

## Redirect Performance

Normal redirect requests:

* perform Redis lookups only
* avoid synchronous SQL writes
* avoid external network calls
* avoid user-agent parsing
* avoid geo lookup

The hot path is intentionally minimal.

---

## Analytics Throughput

Analytics ingestion is designed to reduce database pressure.

### Hot path

* Redis counters absorb high-frequency writes
* Raw events are queued asynchronously
* Redirect latency remains low under traffic spikes

### Background worker

* batch size: up to 500 events
* single transaction persistence
* retry with exponential backoff

---

## Cache Strategy

| Cache                   | Purpose                          |
| ----------------------- | -------------------------------- |
| Redirect cache          | Avoid repeated DB reads          |
| Live analytics counters | Serve dashboards without SQL     |
| Unique visitor sets     | Deduplicate visitors efficiently |
| Rate limit buckets      | Distributed throttling           |

---

# Deployment Notes

## Production Recommendations

### Security

* enable HTTPS only
* set `Secure=true` on cookies
* use `SameSite=Strict` where possible
* rotate JWT secrets periodically
* restrict CORS origins strictly

---

### Database

* enable PostgreSQL connection pooling
* use indexed analytics queries
* configure automated backups
* enable slow query monitoring

---

### Redis

* configure eviction policy carefully
* persist analytics queues
* separate cache and queue namespaces
* monitor memory fragmentation

---

# Future Improvements

## Infrastructure

* Kafka-based analytics ingestion
* Redis Streams migration
* distributed cache invalidation
* multi-region redirect caching
* asynchronous dead-letter queue handling

---

## Product Features

* custom domains
* real-time WebSocket analytics
* QR analytics
* scheduled link expiration jobs
* team workspaces
* webhook integrations
* password-protected links

---

## Observability

* Micrometer metrics
* Prometheus integration
* Grafana dashboards
* distributed tracing
* structured JSON logging
* centralized alerting

---

# Conclusion

TinyRoute focuses on production-style backend engineering patterns rather than simple CRUD functionality.

The system emphasizes:

* low-latency redirects
* Redis-first hot paths
* asynchronous analytics ingestion
* stateless authentication
* distributed rate limiting
* secure token handling
* scalable infrastructure design

The backend architecture is intentionally designed to separate latency-sensitive request handling from expensive enrichment and persistence work, allowing the system to remain responsive even under increasing traffic and analytics load.
