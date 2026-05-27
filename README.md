A full-stack URL shortener with real-time click analytics, QR codes, link previews, and a public bio page — built with Spring Boot and React.

---

## Features

- Shorten URLs with optional custom alias, expiry date, and click limit
- Live analytics dashboard (Redis counters, device/browser/OS/country breakdown)
- Historical analytics with time-bucketed charts and click velocity trend
- QR code generation per link
- OG metadata preview scrape (title, description, image)
- Public bio page — shareable link-in-bio page per user
- JWT auth via HttpOnly cookies (access + refresh token rotation)
- Rate limiting per endpoint, per user or IP (Bucket4j + Redis)
- URL validation: scheme, port, private IP ranges, domain blacklist

---

## Tech stack

| | |
|---|---|
| **Backend** | Java 21, Spring Boot 3.4, Spring Security, Spring Data JPA |
| **Database** | PostgreSQL 15, Flyway migrations |
| **Cache / Queue** | Redis 7 (Lettuce) |
| **Rate limiting** | Bucket4j 8 + Redis (distributed token buckets) |
| **Auth** | JJWT 0.12 — HttpOnly cookie-based, refresh token rotation |
| **Analytics** | Redis hot-path counters + async background worker → PostgreSQL |
| **QR codes** | ZXing 3.5 |
| **Link preview** | Jsoup 1.18 |
| **UA parsing** | ua-parser (uap-java 1.6) |
| **Geo lookup** | ip-api.com |
| **Frontend** | React 18, Vite, Tailwind CSS, React Query, Chart.js |
| **API docs** | Springdoc OpenAPI 2.7 |

---

## Architecture overview

```
React SPA (cookie auth)
        │
        ▼
Spring Boot API
   ├── Auth            JWT cookies, refresh token rotation
   ├── URL Management  create / edit / disable / delete / history
   ├── Redirect        hot path → Redis cache → optional DB fallback
   ├── Analytics       Redis (live) + PostgreSQL (historical)
   └── Public          bio page, QR, preview
        │
   ┌────┴────┐
   │         │
PostgreSQL  Redis
(source of  (redirect cache, analytics counters,
 truth)      rate limit buckets, event queue)
```

---

## Backend highlights

**Redirect hot path** — a redirect touches only Redis (cache lookup + 5 INCR/SADD operations). No synchronous DB writes on normal redirects.

**Analytics pipeline** — raw click events are pushed onto a Redis List (LPUSH). A `@Scheduled` background worker drains batches of up to 500 events every 5 seconds, enriches them with UA parsing and geo lookup, then batch-inserts into PostgreSQL.

**Unique visitor counting** — Redis Sets deduplicate IPs per URL per day on the hot path. The worker persists first-visit records using `ON CONFLICT DO NOTHING`.

**Optimistic locking** — `UrlMapping` carries a `@Version` field. `clickCount` increments via a JPQL `UPDATE ... SET click_count = click_count + 1` to avoid read-modify-write races.

**Rate limiting** — six endpoint groups, each with its own Bucket4j token bucket in Redis, scoped to user ID (authenticated) or hashed IP (public).

---

## Folder structure

```
TinyRoute/
├── backend/
│   ├── src/main/java/com/tinyroute/
│   │   ├── auth/           login, register, refresh, logout
│   │   ├── analytics/      pipeline, Redis, PostgreSQL queries, background worker
│   │   ├── url/            creation, management, preview, validation
│   │   ├── user/           profile, public bio page
│   │   ├── redirect/       GET /{shortUrl} — the hot path
│   │   ├── infra/          cache, network, rate limit, UA parsing
│   │   ├── security/       JWT filter, Spring Security config
│   │   ├── config/         Redis, Async, Bucket4j, Flyway
│   │   └── exception/      global handler, typed exceptions
│   ├── src/test/           WebMvcTest, DataJpaTest, SpringBootTest slices
│   ├── Dockerfile
│   └── docker-compose.yml
└── frontend/
    ├── src/
    │   ├── pages/          Dashboard, Analytics, Profile, BioPage, …
    │   ├── components/     Navbar, charts, URL list, forms
    │   ├── hooks/          useQuery wrappers
    │   └── api/            axios instance (withCredentials, refresh interceptor)
    └── vite.config.js
```

---

## API overview

| Group | Example endpoints |
|-------|------------------|
| Auth | `POST /api/auth/public/login` · `/register` · `/refresh` · `/logout` |
| URLs | `POST /api/urls/shorten` · `GET /api/urls` · `PUT` · `PATCH /disable` · `DELETE` |
| Analytics | `GET /api/urls/analytics/{shortUrl}` · `/analytics/live/{shortUrl}` · `/total-clicks` |
| Public | `GET /{shortUrl}` · `/api/urls/{shortUrl}/qr` · `/preview` · `/api/public/users/{username}` |

Full docs at `http://localhost:8080/swagger-ui.html` when the backend is running.

---

## Local setup

**Docker (recommended)**

```bash
cd backend
docker compose up --build
```

API → `http://localhost:8080`   Swagger → `http://localhost:8080/swagger-ui.html`

**Without Docker** — requires Java 21, PostgreSQL 15, Redis 7

```bash
createdb tinyroute
cd backend
./mvnw spring-boot:run
```

**Frontend**

```bash
cd frontend
npm install
npm run dev
```

---

## Environment variables

| Variable | Default | Notes |
|----------|---------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/tinyroute` | |
| `SPRING_DATASOURCE_USERNAME` | `root` | |
| `SPRING_DATASOURCE_PASSWORD` | `12345678` | **change in production** |
| `JWT_SECRET` | *(required)* | Base64, min 32 bytes |
| `JWT_EXPIRATION` | `172800000` | Access token TTL (ms) |
| `FRONTEND_URL` | `http://localhost:5173/` | CORS allowed origin |
| `SPRING_DATA_REDIS_HOST` | `localhost` | |
| `SPRING_DATA_REDIS_PORT` | `6379` | |
| `SPRING_DATA_REDIS_PASSWORD` | *(empty)* | |
| `SPRING_DATA_REDIS_SSL` | `false` | |

The Docker Compose file sets all of these for local use. Never commit production values.

## Deployment

| Service  | Platform   |
|----------|------------|
| Frontend | Vercel     |
| Backend  | Render     |
| Database | PostgreSQL |
| Resdis   | UPSTASH    |

## Backend Documentation

Detailed backend internals:

- redirect hot path
- analytics pipeline
- JWT rotation
- concurrency handling
- security model

See:
backend/README.md