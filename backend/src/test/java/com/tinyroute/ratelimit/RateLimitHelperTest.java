package com.tinyroute.ratelimit;

import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.exception.RateLimitExceededException;
import com.tinyroute.exception.response.ApiErrorResponse;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.user.entity.Role;
import com.tinyroute.user.entity.User;
import com.tinyroute.user.service.UserService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitHelperTest {

    @Mock
    private UserService userService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private ClientIpService clientIpService;

    @InjectMocks
    private RateLimitHelper rateLimitHelper;

    private User userWithRole(Role role) {
        User u = new User();
        u.setId(1L);
        u.setUsername("alice");
        u.setRole(role);
        return u;
    }

    private static ConsumptionProbe consumedProbe(long remaining) {
        return ConsumptionProbe.consumed(remaining, 1_000_000_000L);
    }

    private static ConsumptionProbe rejectedProbe(long nanosToWaitForRefill) {
        return ConsumptionProbe.rejected(0, nanosToWaitForRefill, nanosToWaitForRefill);
    }

    // ─── getRateLimitResult ───────────────────────────────────────────────────────

    @Test
    void getRateLimitResult_adminUser_bypassesBucket() {
        Principal principal = () -> "admin";
        when(userService.findByUsername("admin")).thenReturn(userWithRole(Role.ROLE_ADMIN));

        RateLimitHelper.RateLimitResult result =
                rateLimitHelper.getRateLimitResult(principal, RateLimitEndpoint.SHORTEN);

        assertTrue(result.isAdmin());
        assertNull(result.probe());
    }

    @Test
    void getRateLimitResult_normalUser_consumesBucket() {
        Principal principal = () -> "alice";
        when(userService.findByUsername("alice")).thenReturn(userWithRole(Role.ROLE_USER));

        Bucket bucket = org.mockito.Mockito.mock(Bucket.class);
        ConsumptionProbe probe = consumedProbe(49);
        when(rateLimitService.resolveBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        RateLimitHelper.RateLimitResult result =
                rateLimitHelper.getRateLimitResult(principal, RateLimitEndpoint.SHORTEN);

        assertFalse(result.isAdmin());
        assertEquals(probe, result.probe());
    }

    @Test
    void getRateLimitResult_nullPrincipal_throwsUnauthorized() {
        ApiException ex = assertThrows(ApiException.class,
                () -> rateLimitHelper.getRateLimitResult(null, RateLimitEndpoint.SHORTEN));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
        assertEquals(ErrorCodes.AUTHENTICATION_FAILED, ex.getErrorCode());
    }

    // ─── enforceLimit ─────────────────────────────────────────────────────────────

    @Test
    void enforceLimit_consumedProbe_doesNotThrow() {
        RateLimitHelper.RateLimitResult result = new RateLimitHelper.RateLimitResult(
                userWithRole(Role.ROLE_USER), false, consumedProbe(10), RateLimitPlan.SHORTEN);
        assertDoesNotThrow(() -> rateLimitHelper.enforceLimit(result, RateLimitEndpoint.SHORTEN));
    }

    @Test
    void enforceLimit_emptyBucket_throwsRateLimitExceeded() {
        RateLimitHelper.RateLimitResult result = new RateLimitHelper.RateLimitResult(
                userWithRole(Role.ROLE_USER), false, rejectedProbe(5_000_000_000L), RateLimitPlan.SHORTEN);
        assertThrows(RateLimitExceededException.class,
                () -> rateLimitHelper.enforceLimit(result, RateLimitEndpoint.SHORTEN));
    }

    @Test
    void enforceLimit_adminResult_neverThrows() {
        RateLimitHelper.RateLimitResult result =
                new RateLimitHelper.RateLimitResult(userWithRole(Role.ROLE_ADMIN), true, null, null);
        assertDoesNotThrow(() -> rateLimitHelper.enforceLimit(result, RateLimitEndpoint.SHORTEN));
    }

    // ─── buildRateLimitHeaders ────────────────────────────────────────────────────

    @Test
    void buildRateLimitHeaders_consumedProbe_setsLimitAndRemaining() {
        HttpHeaders headers = rateLimitHelper.buildRateLimitHeaders(consumedProbe(49), RateLimitPlan.SHORTEN);

        assertEquals("50", headers.getFirst("X-RateLimit-Limit"));
        assertEquals("49", headers.getFirst("X-RateLimit-Remaining"));
        assertNull(headers.getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void buildRateLimitHeaders_nullProbe_returnsEmptyHeaders() {
        HttpHeaders headers = rateLimitHelper.buildRateLimitHeaders(null, RateLimitPlan.SHORTEN);
        assertTrue(headers.isEmpty());
    }

    // ─── applyPublicRateLimit ─────────────────────────────────────────────────────

    @Test
    void applyPublicRateLimit_bucketAvailable_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(clientIpService.resolveClientIp(any())).thenReturn("1.2.3.4");
        when(clientIpService.hashIp("1.2.3.4")).thenReturn("ip-hash");

        Bucket bucket = org.mockito.Mockito.mock(Bucket.class);
        when(rateLimitService.resolveBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumedProbe(59));

        ResponseEntity<ApiErrorResponse> response =
                rateLimitHelper.applyPublicRateLimit(request, RateLimitEndpoint.REDIRECT, "/abc");

        assertNull(response);
    }

    @Test
    void applyPublicRateLimit_bucketEmpty_returns429WithApiErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(clientIpService.resolveClientIp(any())).thenReturn("1.2.3.4");
        when(clientIpService.hashIp("1.2.3.4")).thenReturn("ip-hash");

        Bucket bucket = org.mockito.Mockito.mock(Bucket.class);
        when(rateLimitService.resolveBucket(anyString(), any())).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(rejectedProbe(30_000_000_000L));

        ResponseEntity<ApiErrorResponse> response =
                rateLimitHelper.applyPublicRateLimit(request, RateLimitEndpoint.REDIRECT, "/abc");

        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCodes.RATE_LIMIT_EXCEEDED, response.getBody().getError());
    }

    // ─── extractRetryAfterSeconds ─────────────────────────────────────────────────

    @Test
    void extractRetryAfterSeconds_convertsNanosToSeconds() {
        assertEquals(30L, rateLimitHelper.extractRetryAfterSeconds(rejectedProbe(30_000_000_000L)));
    }

    @Test
    void extractRetryAfterSeconds_nullProbe_returnsZero() {
        assertEquals(0L, rateLimitHelper.extractRetryAfterSeconds(null));
    }
}
