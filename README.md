# TinyRoute

TinyRoute is a backend-focused URL shortening platform built with Spring Boot. It provides authenticated URL management, public redirects, QR code generation, link previews, public bio pages, rate limiting, and detailed click analytics.

The repository also includes a React + Vite frontend client for interacting with the API.

## Backend Features

- JWT authentication with stateless Spring Security
- User registration, login, and profile management
- Short URL creation with custom aliases, titles, expiry dates, and max-click limits
- URL edit, enable, disable, delete, detail, and history endpoints
- Public redirect endpoint with status-aware responses for disabled, expired, and click-limited links
- Click tracking with unique visitor registration
- Async analytics event recording
- Analytics by time bucket, country, device, browser, operating system, and referrer
- Click velocity and peak activity metrics
- Public QR code endpoint returning PNG images
- Public link preview endpoint using destination metadata
- Public user bio profile with visible links
- Redis-backed rate limiting with Bucket4j
- Centralized API exceptions and structured error responses
- Controller, service, repository, and integration tests

## Backend Tech Stack

- Java 21
- Spring Boot 3.4
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Redis
- Bucket4j
- JWT with `jjwt`
- ZXing for QR code generation
- Jsoup for URL preview metadata
- ua-parser for device/browser parsing
- Springdoc OpenAPI
- JUnit, Spring MVC Test, H2, and Mockito for tests

## Project Structure

```txt
TinyRoute/
├── backend/
│   ├── src/main/java/com/tinyroute/
│   │   ├── common/       # shared URL, time, and code-generation utilities
│   │   ├── config/       # application, async, Redis, Swagger, and blacklist config
│   │   ├── controller/   # REST controllers grouped by feature
│   │   ├── dto/          # request and response DTOs
│   │   ├── entity/       # JPA entities
│   │   ├── exception/    # API exceptions and global error handling
│   │   ├── infra/        # QR, geo, network, rate-limit, and user-agent helpers
│   │   ├── mapper/       # entity-to-DTO mapping
│   │   ├── repository/   # JPA repositories
│   │   ├── security/     # JWT filter, security config, and user details
│   │   └── service/      # business logic
│   └── src/test/java/    # controller, service, repository, and integration tests
└── frontend/             # React + Vite client
```

## Core Backend Domain

TinyRoute stores URLs in `UrlMapping`, users in `User`, click analytics in `ClickEvent`, edit history in `UrlEditHistory`, and unique visitor records in `UrlUniqueVisitor`.

Important `UrlMapping` fields include:

- `originalUrl` - destination URL
- `shortUrl` - unique short code or custom alias
- `title` - optional display title
- `clickCount` - unique click count
- `expiresAt` - optional expiry timestamp
- `maxClicks` - optional click limit
- `status` - `ACTIVE`, `DISABLED`, `EXPIRED`, or `CLICK_LIMIT_REACHED`
- `lastClickedAt` - latest successful visit timestamp

## Backend API Overview

Public endpoints:

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/auth/public/register` | Create a user account |
| `POST` | `/api/auth/public/login` | Authenticate and return a JWT |
| `GET` | `/{shortUrl}` | Redirect to the original URL |
| `GET` | `/api/urls/{shortUrl}/preview` | Return destination metadata |
| `GET` | `/api/urls/{shortUrl}/qr` | Return a PNG QR code |
| `GET` | `/api/public/users/{username}` | Return a public bio profile |

Authenticated endpoints require:

```http
Authorization: Bearer <jwt>
```

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/auth/profile` | Get the current profile |
| `PUT` | `/api/auth/profile` | Update profile details |
| `POST` | `/api/urls/shorten` | Create a short URL |
| `GET` | `/api/urls` | List current user's URLs |
| `GET` | `/api/urls/{shortUrl}` | Get URL details |
| `PUT` | `/api/urls/{shortUrl}` | Edit URL destination and title |
| `PATCH` | `/api/urls/{shortUrl}/expiry` | Update URL expiry |
| `PATCH` | `/api/urls/{shortUrl}/disable` | Disable a URL |
| `PATCH` | `/api/urls/{shortUrl}/enable` | Enable a URL |
| `DELETE` | `/api/urls/{shortUrl}` | Delete a URL and related analytics |
| `GET` | `/api/urls/{shortUrl}/history` | Get URL edit history |
| `GET` | `/api/urls/analytics/{shortUrl}` | Get analytics for one URL |
| `GET` | `/api/urls/total-clicks` | Get total clicks grouped by date |

Swagger UI is available when the backend is running:

```txt
http://localhost:8080/swagger-ui.html
```

## Redirect And Analytics Flow

1. A visitor opens `/{shortUrl}`.
2. `RedirectController` resolves the URL through `UrlRedirectService`.
3. The service checks expiry, disabled status, and max-click limits.
4. The client IP is resolved and hashed for unique visitor tracking.
5. Unique visits increment `clickCount`.
6. A click event is recorded asynchronously with IP, user agent, referrer, language, device, browser, OS, and location data.
7. Active links return a `302` redirect. Inactive links return a structured `410` response.

## Rate Limiting

Rate limiting is handled with Redis and Bucket4j. Plans are grouped by endpoint type:

| Plan | Limit |
| --- | --- |
| `AUTH` | 10 requests per minute |
| `SHORTEN` | 50 requests per minute |
| `ANALYTICS` | 20 requests per minute |
| `MY_URLS` | 30 requests per minute |
| `URL_MANAGEMENT` | 20 requests per minute |

Rate-limited responses use HTTP `429`.

## Backend Setup

### Prerequisites

- Java 21
- PostgreSQL
- Redis

Create the database:

```bash
createdb tinyroute
```

Update `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/tinyroute
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

jwt.secret=YOUR_LONG_JWT_SECRET
jwt.expiration=172800000

frontend.url=http://localhost:5173/

spring.data.redis.host=localhost
spring.data.redis.port=6379
```

Start Redis, then run the backend:

```bash
cd backend
./mvnw spring-boot:run
```

The API runs at:

```txt
http://localhost:8080
```

## Backend Tests

Run all backend tests:

```bash
cd backend
./mvnw test
```

The test profile uses H2 in memory. Current test coverage includes:

- Controller tests for auth-protected and public API behavior
- URL creation and management service tests
- Redirect flow and redirect concurrency integration tests
- Analytics service tests
- Repository persistence rule tests
- Global exception handler tests

## Frontend Client

The frontend is a React + Vite app that consumes the backend API.

Frontend stack:

- React 18
- Vite
- React Router
- React Query
- Tailwind CSS
- Chart.js
- Material UI

Install and run:

```bash
cd frontend
npm install
npm run dev
```

Create `frontend/.env`:

```env
VITE_BACKEND_URL=http://localhost:8080
VITE_REACT_SUBDOMAIN=http://url.localhost:5173
```

Frontend routes include:

- `/` - landing page
- `/login` - login
- `/register` - registration
- `/dashboard` - authenticated dashboard
- `/profile` - profile management
- `/bio/:username` - public bio page
- `/analytics/:shortUrl` - link analytics
- `/history/:shortUrl` - edit history
- `/link/:shortUrl` - public link detail with QR and preview

## Useful Commands

Backend:

```bash
cd backend
./mvnw spring-boot:run
./mvnw test
```

Frontend:

```bash
cd frontend
npm run dev
npm run build
npm run lint
```

## Notes

- Do not commit real JWT secrets, database credentials, or production environment values.
- PostgreSQL is used for local backend runtime, while tests use H2.
- Redis must be running for rate limiting.
- Public redirect analytics are recorded asynchronously after a valid link visit.
