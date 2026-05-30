# TinyRoute — Codebase Reference

A URL shortener with analytics, public bio pages, and rate limiting.
Monorepo: `backend/` (Spring Boot) + `frontend/` (React/Vite).

> This file is a navigation + knowledge cache. Verify a fact against source before relying on it for a risky change — code may have moved since this was written.

---

## Backend

**Stack:** Java 21 · Spring Boot 3 · PostgreSQL 15 · Redis 7 · Bucket4j · Flyway · Spring Security · Swagger/OpenAPI · Lombok

**Root package:** `com.tinyroute`

### Package map

| Package | Contents |
|---|---|
| `analytics.controller` | `AnalyticsController` — historical + live endpoints |
| `analytics.service` | `AnalyticsService` (Postgres queries), `RedisAnalyticsService` (hot-path counters + live read), `AnalyticsEventBackgroundWorker` (`@Scheduled` enrichment), `UniqueVisitorRegistrationService` |
| `analytics.infra` | `RedisAnalyticsHelper`, `RedisAnalyticsEventQueue`, `RedisAnalyticsConstants` (key builders + TTLs), `GeoLocationService` (ip-api.com), `UserAgentParsingService`, `ClickEventDataBuilder` |
| `analytics.entity` | `ClickEvent`, `UrlUniqueVisitor` |
| `auth.controller` | `AuthController` — register/login/refresh/logout (cookie-based) |
| `auth.service` | `AuthService`, `RefreshTokenService` (SHA-256 hashed, rotation + reuse detection) |
| `auth.entity` | `RefreshToken` |
| `url.controller` | `UrlCreationController`, `UrlManagementController`, `PreviewController` (preview + QR) |
| `url.service` | `UrlCreationService`, `UrlManagementService`, `UrlPreviewService`, `UrlValidationService` (SSRF guards) |
| `url.validation` | `DomainBlacklistValidator`, `DomainNormalizer` |
| `url.entity` | `UrlMapping` (`@Version` optimistic lock), `UrlStatus`, `UrlEditHistory` |
| `redirect` | `RedirectController` (`GET /{shortUrl}`), `RedirectService` |
| `user` | `UserController` (`/api/auth/profile`), `PublicProfileController` (`/api/public/users/{username}`), `UserService`, `UserMapper` |
| `ratelimit` | `RateLimitHelper`, `RateLimitService`, `RateLimitPlan`, `RateLimitEndpoint` |
| `security` | `WebSecurityConfig`, `JwtAuthenticationFilter`, `JwtService`, `UserDetailsImpl`, `SecurityExceptionHandlers` |
| `exception` | `ApiException` (base, has `httpStatus` + `errorCode`), subclasses, `ErrorCodes`, `ErrorMessages`, `GlobalExceptionHandler`, `response/ApiErrorResponse`, `response/RedirectErrorResponse` |
| `infra.cache` | `RedirectCacheService`, `RedirectCacheEntry` (projection) |
| `infra.network` | `ClientIpService` (IP extraction + SHA-256 hashing) |
| `config` | `CacheConfig`, `AsyncConfig`, `BucketConfig`, `RedisConfig`, `RoleLimitConfig`, `SwaggerConfig`, `AppConfig` |

### Error model

All non-2xx responses use `ApiErrorResponse`: `{ status, error, message, path, timestamp }`.
`error` is always a constant from `ErrorCodes.java`. `GlobalExceptionHandler` (`@RestControllerAdvice`) maps every exception type. `ApiException(httpStatus, errorCode, message)` is the base for domain errors; `AlreadyExistsException`, `UrlException`, `InvalidUrlException`, `InvalidDestinationUrlException`, `DomainBlacklistedException`, `RateLimitExceededException`, `ShortUrlGenerationFailedException` extend it.

Special case: redirect inactive links return `410` with `RedirectErrorResponse { urlStatus, message }` (frontend needs the enum to pick a template).

### Key behaviors

- **Redirect hot path** (`RedirectService.getOriginalUrl`): Redis cache lookup → DB fallback + cache put (TTL 300s default, `cache.redirect.ttl-seconds`). Status resolution can transition ACTIVE→EXPIRED/CLICK_LIMIT_REACHED and evicts cache. Click recorded via 3 Redis ops + queue push — **no** UA parse / geo / DB write on hot path.
- **Analytics Stage 1** (`RedisAnalyticsService.recordClick`): `INCR daily`, `SADD unique`, `HINCRBY hourly`, `LPUSH raw_events`. All analytics TTLs = **48h** (`RedisAnalyticsConstants.DAILY_COUNTER_TTL_SECONDS = 2*24*60*60`).
- **Analytics Stage 2** (`AnalyticsEventBackgroundWorker`, `@Scheduled fixedDelay=5000 initialDelay=2000`): RPOP batch ≤500 → geo + UA enrich → `registerIfFirstVisit` (ON CONFLICT DO NOTHING) → `recordLiveAggregates` (5 dimension hashes) → `saveAll` + bulk `incrementClickCount`/`incrementTotalClickCount`. 3 retries, 1/2/4s backoff.
- **Auth**: access JWT (HMAC-SHA256, 15min, `type=access` claim) + refresh token (32 random bytes hex, SHA-256 stored). Both `HttpOnly SameSite=Lax` cookies. Refresh rotates; reuse of revoked token revokes ALL user tokens.
- **Rate limiting** (Bucket4j in Redis): `AUTH 10/min` & `REDIRECT 60/min` per IP-hash; `SHORTEN 50`, `MY_URLS 30`, `ANALYTICS 20`, `URL_MANAGEMENT 20` per user/min. `ROLE_ADMIN` bypasses authenticated limits.
- **URL validation** (`UrlValidationService`): scheme whitelist (http/https), port whitelist (80/443/none), IDN normalize, static blacklist, DNS resolution rejecting private/loopback/link-local/CGN(100.64/10)/benchmark(198.18/15).
- **`UrlCreationService`**: reserved aliases = {api, admin, login, register, swagger, docs, health, preview, qr}. maxClicks from `RoleLimitConfig` (user 10000 / premium 100000 / admin 100000). Generated alias = 8 chars, 10 collision retries.

### DB schema (Flyway `V1__initial_schema.sql`; entities are source of truth)

`users`, `url_mapping` (status CHECK constraint, `version` lock, `click_count`=unique visitors, `total_click_count`=all clicks), `click_event`, `url_unique_visitor` (UNIQUE `url_mapping_id, ip_hash`), `url_edit_history`, `refresh_token` (UNIQUE `token_hash`).

### API surface (verified)

- Auth: `POST /api/auth/public/{register|login|refresh}`, `POST /api/auth/logout`, `GET|PUT /api/auth/profile`
- URLs: `POST /api/urls/shorten` (**201**), `GET /api/urls`, `GET|PUT|DELETE /api/urls/{shortUrl}`, `PATCH /api/urls/{shortUrl}/{expiry|disable|enable}`, `GET /api/urls/{shortUrl}/history`, `GET /api/urls/total-clicks`
- Public: `GET /{shortUrl}`, `GET /api/urls/{shortUrl}/{preview|qr}`, `GET /api/public/users/{username}`
- Analytics: `GET /api/urls/analytics/{shortUrl}`, `GET /api/urls/analytics/{shortUrl}/live`

Full details + diagrams in `backend/README.md` (kept accurate to code).

---

## Frontend

**Stack:** React 18 · Vite · Tailwind · React Router v7 · React Query v3 · React Hook Form · Material-UI · Chart.js · Axios

**Structure:** `src/{api,components,hooks,pages,utils}` + `App.jsx`, `AppRouter.jsx`, `PrivateRoute.jsx`

### Shared building blocks (post-refactor)

| Path | Purpose |
|---|---|
| `utils/errorHandler.js` | `handleApiError(error, overrides, fallback)` — maps backend `ApiErrorResponse.error` codes → toast messages via central `ERROR_CODE_MESSAGES`; callers pass only context-specific overrides |
| `utils/helper.js` | `isValidHttpUrl`, `getInitials`, subdomain helpers |
| `utils/analyticsTransform.js` | `getDimension`, `toNum`, `formatPeakLabel`, `buildChart` (pure analytics data shaping) |
| `utils/chartConfig.js` | `baseChartOptions`, `integerTickCallback` (shared by both charts) |
| `components/Common/` | `AvatarDisplay`, `PasswordField`, `ErrorMessage`, `ActionButton`, `QueryLayout` (loading→empty→content), `Button`, `TextField`, `Loader`, `EmptyState`, `StatBlock`, `StatusBadge`, `ConfirmDialog`, `DateRangePicker`, `Navbar`, `Footer`, `Logo` |
| `components/Dashboard/` | `ShortenItem` (orchestrator ~120 lines), `ShortenItemHeader`, `ShortenItemActions`, `ShortenItemExpanded`, `CreateNewShorten`, `ShortenPopUp`, `ShortenUrlList`, `Graph` |
| `components/Profile/` | `ProfileHero`, `ProfileLinksSection` |
| `components/Analytics/` | `ClicksLineChart`, `DimensionBar`, `DimensionCard`, `PeakActivityCard`, `VelocityBadge` |
| `hooks/useQuery.js` | All React Query hooks + mutation fns (`createShortUrl`, `editShortUrl`, `deleteShortUrl`, `enable/disableShortUrl`, `updateProfile`, fetch hooks) |
| `hooks/useCopyToClipboard.js` | `{ copied, copy }` auto-reset |
| `hooks/useConfirmAction.js` | `{ open, loading, trigger, close, confirm }` for confirm dialogs |
| `hooks/useShortenForm.js` | Owns the create-URL form (useForm + payload cleanup + submit + error) |

### Conventions

- Server state via React Query; UI state via `useState`; no Redux/Context.
- Auth derived from `useFetchProfile()` + cookies; `api.js` axios interceptor handles 401 refresh.
- Error messages flow from backend error codes through `handleApiError` — avoid hardcoding status-code→string maps in components.
- PropTypes on shared components.

---

## Session history (changes already made)

**Frontend refactor (5 phases, complete):** shared utils + `errorHandler`; extracted Common components; custom hooks; split `ShortenItem` (640→120) and `ProfilePage` (415→75), extracted analytics transform; shared chart config. Fixed pre-existing missing `EmptyState` import in `BioPage.jsx`.

**Backend API fixes applied:**
- `POST /api/urls/shorten` now returns **201** (+ Swagger annotation + test updated).
- Removed try/catch in `RedirectController` 500 path → propagates to `GlobalExceptionHandler`.
- `applyPublicRateLimit` returns `ApiErrorResponse` (not the deleted `RateLimitErrorResponse`); `RateLimitErrorResponse.java` deleted.
- `AnalyticsQueryRequest.hasValidDateRange()` annotated `@AssertTrue`.
- Error-code string literals replaced with `ErrorCodes` constants in `AuthController` + `RefreshTokenService`; added `ErrorCodes.REFRESH_TOKEN_MISSING`.

**Known open items (see `backend/TEST_UPDATE_PLAN.md`):** missing service tests (`RefreshTokenService`, `AuthService`, `UserService`, `UrlValidationService`, `RedisAnalyticsService`, `RateLimitHelper`); `UrlCreationServiceTest` is a stub with no `@Test` methods; `GlobalExceptionHandlerTest` covers only 3 of 9 handlers. Backend API design audit (issues #1–#15) — fixed: 1, 4, 6, 13; suggested-only: 10.

---

## Build / run

- Backend: `cd backend && docker compose up --build` (API 8080, Postgres 5432, Redis 6379). Manual: `./mvnw spring-boot:run`. Swagger: `localhost:8080/swagger-ui.html`.
- Tests: `@WebMvcTest` (controllers), `@DataJpaTest` (repos), `@SpringBootTest` (integration), JUnit5 + Mockito (unit).
