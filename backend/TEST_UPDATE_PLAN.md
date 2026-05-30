# Test Update Plan

20 test files exist. This document lists every required change in priority order:
broken tests that fail right now, empty stubs, entirely missing test classes, and gap scenarios in otherwise-good files.

---

## Part 1 — Tests That Are Currently Broken

These fail against the current codebase and must be fixed first.

---

### 1.1 `UrlCreationControllerWebMvcTest` — wrong status code

**File:** `controller/url/UrlCreationControllerWebMvcTest.java`  
**Method:** `createShortUrl_validRequest_returns200AndResponseBody`

We changed `UrlCreationController` to return `201 CREATED`. The test still expects `200 OK`.

```java
// BEFORE
.andExpect(status().isOk())

// AFTER
.andExpect(status().isCreated())
```

Also rename the test method to match:
```java
// BEFORE
void createShortUrl_validRequest_returns200AndResponseBody()

// AFTER
void createShortUrl_validRequest_returns201AndResponseBody()
```

---

### 1.2 `UrlCreationServiceTest` — no `@Test` methods (stub only)

**File:** `service/url/UrlCreationServiceTest.java`

The class has mocks, `@InjectMocks`, and three helper methods but zero `@Test` methods. It contributes nothing to coverage. See Part 2 for what to add.

---

## Part 2 — Empty Stubs to Fill In

---

### 2.1 `UrlCreationServiceTest` — implement all test methods

**File:** `service/url/UrlCreationServiceTest.java`  
**Class under test:** `UrlCreationService`

The helpers (`userWithRole`, `stubValidation`, `stubMapper`) are already in place. Add these test methods:

| Test method | Scenario | Expected |
|---|---|---|
| `createShortUrl_generatesAlias_whenNoCustomAlias` | request has no alias → service generates one | saved entity has a short 8-char alias, returns DTO |
| `createShortUrl_usesCustomAlias_whenProvided` | `customAlias = "launch"` | saved entity short_url == "launch" |
| `createShortUrl_throwsConflict_whenCustomAliasTaken` | repo returns existing mapping with the alias | `ApiException` 409 `DUPLICATE_ALIAS` |
| `createShortUrl_throwsConflict_whenDuplicateOriginalUrl` | user already has active link for same URL | `ApiException` 409 `URL_ALREADY_EXISTS` |
| `createShortUrl_appliesMaxClicksFromPlan` | user is `ROLE_USER` + request maxClicks 0 | saved entity maxClicks == plan default |
| `createShortUrl_respectsRequestedMaxClicks_whenWithinPlan` | user is `ROLE_PREMIUM`, request maxClicks == 500 | saved entity maxClicks == 500 |
| `createShortUrl_blocksReservedAlias` | customAlias is "api" or "admin" | `ApiException` 400 or 409 |
| `createShortUrl_setsExpiresAt_whenProvided` | request has `expiresAt` | saved entity expiresAt matches |
| `createShortUrl_callsValidationService` | any valid request | `urlValidationService.validateAndNormalizeDestinationUrl` called once |

---

## Part 3 — Missing Test Classes (create from scratch)

In priority order.

---

### 3.1 `RefreshTokenServiceTest` ← highest priority (security-critical)

**File to create:** `service/auth/RefreshTokenServiceTest.java`  
**Class under test:** `RefreshTokenService`  
**Framework:** `@ExtendWith(MockitoExtension.class)`, mock `RefreshTokenRepository`

| Test method | Scenario | Expected |
|---|---|---|
| `rotateToken_validToken_returnsNewTokenAndUser` | hash found, not revoked, not expired | returns `RotationResult` with user + new raw token; old token marked revoked |
| `rotateToken_revokedToken_revokesAllAndThrows` | `existing.isRevoked() == true` | calls `revokeAllActiveForUser`; throws `ApiException` 401 `REFRESH_TOKEN_REVOKED` |
| `rotateToken_expiredToken_throwsExpired` | `existing.expiresAt.isBefore(now)` | throws `ApiException` 401 `REFRESH_TOKEN_EXPIRED` |
| `rotateToken_unknownToken_throwsInvalid` | repo returns empty Optional | throws `ApiException` 401 `INVALID_REFRESH_TOKEN` |
| `rotateToken_savesNewTokenAfterRotation` | valid rotation | `refreshTokenRepository.save` called twice (revoke old, save new) |
| `createRefreshToken_savesHashedToken` | any user | saves entity with non-null `tokenHash`; raw token is returned; raw != hash |
| `createRefreshToken_rawTokenIsNotStoredDirectly` | any user | captured `tokenHash` != the returned raw string |
| `revoke_existingToken_marksRevoked` | hash found | entity `revoked = true` saved |
| `revoke_unknownToken_doesNothing` | hash not found | no exception, no save |
| `hashToken_deterministicForSameInput` | same string hashed twice | both results equal |
| `hashToken_differentForDifferentInputs` | two different strings | hashes differ |

---

### 3.2 `AuthServiceTest`

**File to create:** `service/auth/AuthServiceTest.java`  
**Class under test:** `AuthService`  
**Framework:** `@ExtendWith(MockitoExtension.class)`, mocks for `AuthenticationManager`, `JwtService`, `RefreshTokenService`, `UserRepository`

| Test method | Scenario | Expected |
|---|---|---|
| `authenticateUser_validCredentials_returnsAuthResponse` | `authenticationManager.authenticate` succeeds | `AuthResponse` has non-null `token` and `refreshToken` |
| `authenticateUser_badCredentials_throwsBadCredentials` | auth manager throws `BadCredentialsException` | propagates as-is (caught by GlobalExceptionHandler) |
| `registerUser_newUser_savesWithBcryptPassword` | username and email not taken | `userRepository.save` called; saved password != raw password |
| `registerUser_duplicateUsername_throwsConflict` | `userRepository.existsByUsername` returns true | `ApiException` 409 `USERNAME_ALREADY_EXISTS` |
| `registerUser_duplicateEmail_throwsConflict` | `userRepository.existsByEmail` returns true | `ApiException` 409 `EMAIL_ALREADY_EXISTS` |
| `logout_validToken_revokesRefreshToken` | token found in request | `refreshTokenService.revoke` called with the raw token |
| `logout_missingToken_noOp` | token is null/blank | `refreshTokenService.revoke` never called; no exception |

---

### 3.3 `UserServiceTest`

**File to create:** `service/user/UserServiceTest.java`  
**Class under test:** `UserService`  
**Framework:** `@ExtendWith(MockitoExtension.class)`, mock `UserRepository`

| Test method | Scenario | Expected |
|---|---|---|
| `getProfile_existingUser_returnsProfileDTO` | user found | `UserProfileDTO` with correct username, bio |
| `getProfile_unknownUser_throws` | repo returns empty | `UsernameNotFoundException` |
| `updateProfile_validRequest_updatesBioAndAvatar` | user found, valid avatar URL | saved entity has updated bio + avatarUrl |
| `updateProfile_unknownUser_throws` | repo returns empty | `UsernameNotFoundException` |
| `getPublicProfile_existingUser_incrementsBioPageViews` | user found | `bioPageViews` incremented and saved; response contains public URLs |
| `getPublicProfile_unknownUser_throws` | repo returns empty | `UsernameNotFoundException` |
| `findByUsername_found_returnsUser` | repo finds user | returns the User entity |
| `findByUsername_notFound_throws` | repo returns empty | `UsernameNotFoundException` |

---

### 3.4 `UrlValidationServiceTest`

**File to create:** `service/url/UrlValidationServiceTest.java`  
**Class under test:** `UrlValidationService`  
**Framework:** `@ExtendWith(MockitoExtension.class)` (DNS resolution can be tested with real localhost calls or mocked)

| Test method | Scenario | Expected |
|---|---|---|
| `validate_httpUrl_passes` | `http://example.com` | no exception |
| `validate_httpsUrl_passes` | `https://example.com` | no exception |
| `validate_javascriptScheme_throws` | `javascript:alert(1)` | `InvalidUrlException` or `InvalidDestinationUrlException` |
| `validate_ftpScheme_throws` | `ftp://example.com` | exception |
| `validate_localhostIp_throws` | `http://127.0.0.1` | `InvalidDestinationUrlException` (SSRF) |
| `validate_privateIp_throws` | `http://192.168.1.1` | `InvalidDestinationUrlException` (SSRF) |
| `validate_blacklistedDomain_throws` | domain on static blacklist | `DomainBlacklistedException` |
| `validate_nonStandardPort_throws` | `http://example.com:9999` | exception (port not whitelisted) |
| `validate_standardPort443_passes` | `https://example.com:443` | no exception |
| `validate_idnDomain_normalised` | Unicode domain | normalised to punycode without exception |

---

### 3.5 `RedisAnalyticsServiceTest`

**File to create:** `analytics/service/RedisAnalyticsServiceTest.java`  
**Class under test:** `RedisAnalyticsService`  
**Framework:** `@ExtendWith(MockitoExtension.class)`, mock `RedisAnalyticsHelper`, `RedisAnalyticsEventQueue`

| Test method | Scenario | Expected |
|---|---|---|
| `recordClick_validEvent_incrementsDailyCounter` | normal click | `helper.incrementCounter(dailyKey, 48h TTL)` called |
| `recordClick_validEvent_addsToUniqueSet` | normal click | `helper.addToSet(uniqueKey, ipHash, 48h TTL)` called |
| `recordClick_validEvent_incrementsHourlyHash` | normal click | `helper.incrementHash(hourlyKey, "HH", 48h TTL)` called |
| `recordClick_validEvent_enqueuesToRawQueue` | normal click | `eventQueue.enqueue(event)` called |
| `recordClick_redisError_doesNotThrow` | helper throws | exception swallowed, no propagation |
| `recordLiveAggregates_callsAllDimensionHashes` | valid params | country, device, browser, os, referrer hashes all incremented |
| `getLiveAnalytics_returnsPopulatedResponse` | counters return mock values | `todayClicks`, `todayUniqueVisitors`, `hourlyClicks` maps populated |
| `getLiveAnalytics_redisError_returnsEmptyResponse` | helper throws | returns empty `LiveAnalyticsResponse`, no exception |

---

### 3.6 `RateLimitHelperTest`

**File to create:** `ratelimit/RateLimitHelperTest.java`  
**Class under test:** `RateLimitHelper`  
**Framework:** `@ExtendWith(MockitoExtension.class)`, mocks `UserService`, `RateLimitService`, `ClientIpService`

| Test method | Scenario | Expected |
|---|---|---|
| `getRateLimitResult_adminUser_returnsAdminResultWithNullProbe` | user role is `ROLE_ADMIN` | `isAdmin = true`, `probe = null` |
| `getRateLimitResult_normalUser_consumesBucket` | probe is consumed | `isAdmin = false`, probe returned |
| `getRateLimitResult_nullPrincipal_throwsUnauthorized` | `principal == null` | `ApiException` 401 `AUTHENTICATION_FAILED` |
| `enforceLimit_consumedProbe_doesNotThrow` | `probe.isConsumed() == true` | no exception |
| `enforceLimit_emptyBucket_throwsRateLimitExceeded` | `probe.isConsumed() == false` | `RateLimitExceededException` thrown |
| `enforceLimit_adminUser_neverThrows` | `isAdmin = true`, probe is null | no exception |
| `buildRateLimitHeaders_consumedProbe_setsLimitAndRemaining` | probe consumed | `X-RateLimit-Limit` and `X-RateLimit-Remaining` headers present, no `Retry-After` |
| `buildRateLimitHeaders_nullProbe_returnsEmptyHeaders` | probe is null | empty `HttpHeaders` |
| `applyPublicRateLimit_bucketAvailable_returnsNull` | probe consumed | returns `null` (allow through) |
| `applyPublicRateLimit_bucketEmpty_returns429WithApiErrorResponse` | probe not consumed | returns `ResponseEntity` with status 429; body is `ApiErrorResponse` with `RATE_LIMIT_EXCEEDED` code |
| `extractRetryAfterSeconds_probeWithNanos_returnsCorrectSeconds` | `nanosToWait = 30_000_000_000L` | returns 30 |
| `extractRetryAfterSeconds_nullProbe_returnsZero` | null | returns 0 |

---

## Part 4 — Gap Scenarios in Existing Test Classes

These files are good but missing specific scenarios introduced by recent changes.

---

### 4.1 `GlobalExceptionHandlerTest` — add missing handler tests

**File:** `exception/GlobalExceptionHandlerTest.java`

| Add test for | Handler method | What to assert |
|---|---|---|
| `handleApiException` with plain `ApiException` | `handleApiException` | status, error code, message, path all match exception |
| `handleApiException` with `RateLimitExceededException` | `handleApiException` | response has `Retry-After` header set to the seconds value |
| `handleValidation` with field errors | `handleValidation` | status 400, error `VALIDATION_ERROR`, message contains `"fieldName: message"` format |
| `handleMalformedBody` | `handleMalformedBody` | status 400, error `MALFORMED_REQUEST_BODY` |
| `handleAuthentication` | `handleAuthentication` | status 401, error `AUTHENTICATION_FAILED` |
| `handleGeneric` | `handleGeneric` | status 500, error `INTERNAL_ERROR` |

---

### 4.2 `AnalyticsControllerWebMvcTest` — add `@AssertTrue` validation test

**File:** `controller/analytics/AnalyticsControllerWebMvcTest.java`

`AnalyticsQueryRequest.hasValidDateRange()` is now annotated `@AssertTrue`. The controller already uses `@Valid`. Add:

```java
@Test
void getAnalytics_endDateBeforeStartDate_returns400() throws Exception {
    mockMvc.perform(get("/api/urls/analytics/abc12345")
                    .param("startDate", "2026-05-01T00:00:00")
                    .param("endDate",   "2026-04-01T00:00:00")
                    .principal(() -> "alice"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
}
```

---

### 4.3 `AuthControllerWebMvcTest` — verify rate-limit response shape on login

**File:** `controller/auth/AuthControllerWebMvcTest.java`

`applyPublicRateLimit` now returns `ResponseEntity<ApiErrorResponse>` (was `RateLimitErrorResponse`). Verify the 429 body shape:

```java
@Test
void login_rateLimited_returns429WithApiErrorResponseShape() throws Exception {
    // stub applyPublicRateLimit to return a 429 ResponseEntity<ApiErrorResponse>
    // assert response body has "status", "error", "message", "path", "timestamp"
    // assert "error" == "RATE_LIMIT_EXCEEDED"
}
```

---

### 4.4 `RedirectControllerWebMvcTest` — add exception propagation test

**File:** `controller/redirect/RedirectControllerWebMvcTest.java`

We removed the try-catch that swallowed unexpected exceptions. Now they propagate to `GlobalExceptionHandler`. Add:

```java
@Test
void redirect_serviceThrowsUnexpectedException_returns500() throws Exception {
    when(redirectService.getOriginalUrl(eq("boom"), any()))
            .thenThrow(new RuntimeException("unexpected"));

    mockMvc.perform(get("/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
}
```

This test requires `@Import(GlobalExceptionHandler.class)` added to the class — currently missing.

---

### 4.5 `UrlManagementControllerWebMvcTest` — verify `GET /total-clicks` date validation

**File:** `controller/url/UrlManagementControllerWebMvcTest.java`

`GET /api/urls/total-clicks` validates `endDate >= startDate` at the service layer. Add:

```java
@Test
void getTotalClicks_endBeforeStart_returns400() throws Exception {
    mockMvc.perform(get("/api/urls/total-clicks")
                    .param("startDate", "2026-05-01")
                    .param("endDate",   "2026-04-01")
                    .principal(() -> "alice"))
            .andExpect(status().isBadRequest());
}

@Test
void getTotalClicks_missingStartDate_returns400() throws Exception {
    mockMvc.perform(get("/api/urls/total-clicks")
                    .param("endDate", "2026-05-01")
                    .principal(() -> "alice"))
            .andExpect(status().isBadRequest());
}
```

---

### 4.6 `RefreshTokenServiceTest` (after creation) — verify `ErrorCodes` constants used

When checking thrown exceptions, assert the `error` field matches the constants, not hardcoded strings:

```java
// use ErrorCodes.REFRESH_TOKEN_REVOKED not "REFRESH_TOKEN_REVOKED"
assertThat(ex.getErrorCode()).isEqualTo(ErrorCodes.REFRESH_TOKEN_REVOKED);
```

---

### 4.7 `PersistenceRulesDataJpaTest` — add `url_edit_history` index test

**File:** `repository/PersistenceRulesDataJpaTest.java`

The `url_edit_history` table has indexes on both `url_mapping_id` and `changed_at`. Add a query-based test that verifies history retrieval by date range uses the index (or simply that the repository query returns correct results after multiple edits to the same mapping).

---

## Part 5 — Integration Test Gaps

---

### 5.1 `RedirectFlowIntegrationTest` — add auth-wall and live-analytics scenarios

**File:** `integration/RedirectFlowIntegrationTest.java`

Only covers 302 redirect and click-limit edge case. Add:

| Test | What to verify |
|---|---|
| `redirect_disabledLink_returns410` | end-to-end: create → disable → redirect → 410 |
| `redirect_expiredLink_returns410` | create with past `expiresAt` → redirect → 410 |
| `redirect_recordsClickInRedis` | after redirect, verify `todayClicks` incremented via `/live` endpoint |
| `redirect_rateLimited_returns429` | hammer same IP → eventually 429 with `Retry-After` header |

---

### 5.2 `RedirectConcurrencyIntegrationTest` — add token rotation concurrency test

**File:** `integration/RedirectConcurrencyIntegrationTest.java`

Add a concurrent refresh token scenario: 2 threads attempt to refresh the same token simultaneously — only one should succeed; the other should get 401 `REFRESH_TOKEN_REVOKED` (because the token is marked revoked immediately after first use, triggering reuse detection for the second).

---

## Execution Order

| Order | Action | Why |
|---|---|---|
| 1 | Fix `UrlCreationControllerWebMvcTest` status 200 → 201 | Currently failing |
| 2 | Implement `UrlCreationServiceTest` | Stub contributes zero coverage |
| 3 | Create `RefreshTokenServiceTest` | Security-critical, no coverage |
| 4 | Create `AuthServiceTest` | Core auth path, no coverage |
| 5 | Add `GlobalExceptionHandlerTest` missing handlers | Small additions to existing file |
| 6 | Add `RedirectControllerWebMvcTest` exception propagation test | Validates the removed try-catch behaviour |
| 7 | Add `AnalyticsControllerWebMvcTest` date validation test | Validates new `@AssertTrue` constraint |
| 8 | Add `AuthControllerWebMvcTest` rate-limit body shape test | Validates `ApiErrorResponse` change |
| 9 | Create `UserServiceTest` | Profile logic untested |
| 10 | Create `UrlValidationServiceTest` | SSRF + blacklist logic untested |
| 11 | Create `RedisAnalyticsServiceTest` | Analytics pipeline stage 1 untested |
| 12 | Create `RateLimitHelperTest` | Rate limiting logic untested |
| 13 | Add `UrlManagementControllerWebMvcTest` date validation tests | Gap scenarios |
| 14 | Expand integration tests | End-to-end coverage |

---

## New Files to Create

```
src/test/java/com/tinyroute/
├── service/auth/
│   ├── AuthServiceTest.java             (new)
│   └── RefreshTokenServiceTest.java     (new)
├── service/user/
│   └── UserServiceTest.java             (new)
├── service/url/
│   └── UrlValidationServiceTest.java    (new)
├── analytics/service/
│   └── RedisAnalyticsServiceTest.java   (new)
└── ratelimit/
    └── RateLimitHelperTest.java         (new)
```

## Files to Modify

```
controller/url/UrlCreationControllerWebMvcTest.java   fix 200 → 201
controller/redirect/RedirectControllerWebMvcTest.java add exception propagation test + @Import GlobalExceptionHandler
controller/analytics/AnalyticsControllerWebMvcTest.java add date-range validation test
controller/auth/AuthControllerWebMvcTest.java         add rate-limit body shape test
controller/url/UrlManagementControllerWebMvcTest.java add total-clicks date validation tests
exception/GlobalExceptionHandlerTest.java             add 6 missing handler tests
service/url/UrlCreationServiceTest.java               add all @Test methods (stub → real)
```
