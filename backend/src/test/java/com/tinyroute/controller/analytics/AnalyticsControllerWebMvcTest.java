package com.tinyroute.controller.analytics;

import com.tinyroute.controller.url.UrlRateLimitHelper;
import com.tinyroute.infra.ratelimit.RateLimitEndpoint;
import com.tinyroute.dto.analytics.response.LinkAnalyticsResponse;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.GlobalExceptionHandler;
import com.tinyroute.exception.RateLimitExceededException;
import com.tinyroute.service.analytics.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AnalyticsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private UrlRateLimitHelper rateLimitHelper;

    /** Produces a non-rate-limited RateLimitResult for a given user. */
    private UrlRateLimitHelper.RateLimitResult allowedResult(User user) {
        // RateLimitResult record signature: (User user, boolean isAdmin, ConsumptionProbe probe, RateLimitPlan limit)
        // isAdmin = true → controller short-circuits rate-limit check and adds empty headers
        return new UrlRateLimitHelper.RateLimitResult(user, true, null, null);
    }

    private User testUser(String username) {
        User user = new User();
        user.setId(101L);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setRole(Role.ROLE_USER);
        return user;
    }

    @BeforeEach
    void setUp() {
        // Default: every request for "alice" is rate-limit-allowed (admin bypass)
        User alice = testUser("alice");
        when(rateLimitHelper.getRateLimitResult(any(Principal.class), eq(RateLimitEndpoint.ANALYTICS)))
                .thenReturn(allowedResult(alice));
    }

    // ─── 200 OK ──────────────────────────────────────────────────────────────────

    @Test
    void getUserAnalytics_validRequest_returns200WithBody() throws Exception {
        LinkAnalyticsResponse response = new LinkAnalyticsResponse();
        response.setTotalClicks(10);
        response.setUniqueClicks(4);

        // Correct signature: getAnalytics(String shortUrl, AnalyticsQueryRequest request, String username)
        when(analyticsService.getAnalytics(eq("abc12345"), any(), eq("alice")))
                .thenReturn(response);

        mockMvc.perform(get("/api/urls/analytics/abc12345")
                        .principal(() -> "alice")
                        .param("startDate", "2026-03-01T00:00:00")
                        .param("endDate", "2026-03-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(10))
                .andExpect(jsonPath("$.uniqueClicks").value(4));
    }

    @Test
    void getUserAnalytics_withNoDateParams_returns200() throws Exception {
        // When no dates are provided, request fields are null — service uses defaults
        LinkAnalyticsResponse response = new LinkAnalyticsResponse();
        response.setTotalClicks(99);
        response.setUniqueClicks(20);
        when(analyticsService.getAnalytics(eq("abc12345"), any(), eq("alice")))
                .thenReturn(response);

        mockMvc.perform(get("/api/urls/analytics/abc12345")
                        .principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(99));
    }

    // ─── 404 Not Found ───────────────────────────────────────────────────────────

    @Test
    void getUserAnalytics_whenLinkNotFound_returns404() throws Exception {
        // The service throws ApiException(NOT_FOUND) — GlobalExceptionHandler returns 404.
        // The controller does NOT check for null — stubbing null would return 200, which is wrong.
        when(analyticsService.getAnalytics(eq("missing"), any(), eq("alice")))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Link not found."));

        mockMvc.perform(get("/api/urls/analytics/missing")
                        .principal(() -> "alice")
                        .param("startDate", "2026-03-01T00:00:00")
                        .param("endDate", "2026-03-31T23:59:59"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("LINK_NOT_FOUND"));
    }

    // ─── 400 Bad Request ─────────────────────────────────────────────────────────

    @Test
    void getUserAnalytics_invalidDateFormat_returns400() throws Exception {
        // "not-a-date" cannot be parsed as LocalDateTime →
        // MethodArgumentTypeMismatchException → GlobalExceptionHandler → 400
        mockMvc.perform(get("/api/urls/analytics/abc12345")
                        .principal(() -> "alice")
                        .param("startDate", "not-a-date")
                        .param("endDate", "2026-03-31T23:59:59"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserAnalytics_whenServiceThrowsInvalidDateRange_returns400() throws Exception {
        // Service throws BAD_REQUEST when end <= start
        when(analyticsService.getAnalytics(eq("abc12345"), any(), eq("alice")))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                        "endDate must be after startDate."));

        mockMvc.perform(get("/api/urls/analytics/abc12345")
                        .principal(() -> "alice")
                        .param("startDate", "2026-03-31T00:00:00")
                        .param("endDate", "2026-03-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_DATE_RANGE"));
    }

    // ─── 429 Rate Limited ────────────────────────────────────────────────────────

    @Test
    void getUserAnalytics_whenRateLimitExceeded_returns429() throws Exception {
        // Simulate a CONSUMED probe (isConsumed() == false means the token was NOT granted)
        io.github.bucket4j.ConsumptionProbe exhaustedProbe =
                org.mockito.Mockito.mock(io.github.bucket4j.ConsumptionProbe.class);
        org.mockito.Mockito.when(exhaustedProbe.isConsumed()).thenReturn(false);
        org.mockito.Mockito.when(exhaustedProbe.getRemainingTokens()).thenReturn(0L);
        org.mockito.Mockito.when(exhaustedProbe.getNanosToWaitForRefill()).thenReturn(5_000_000_000L);

        User alice = testUser("alice");
        // isAdmin = false → rate limit is enforced; probe shows not consumed
        UrlRateLimitHelper.RateLimitResult rateLimited =
                new UrlRateLimitHelper.RateLimitResult(alice, false, exhaustedProbe, null);

        when(rateLimitHelper.getRateLimitResult(any(Principal.class), eq(RateLimitEndpoint.ANALYTICS)))
                .thenReturn(rateLimited);
        when(rateLimitHelper.buildRateLimitHeaders(any(), any()))
                .thenReturn(new org.springframework.http.HttpHeaders());
        org.mockito.Mockito.doThrow(new RateLimitExceededException("Too many requests for analytics. Please try again later.", 5))
                .when(rateLimitHelper).enforceLimit(any(), eq(RateLimitEndpoint.ANALYTICS));

        mockMvc.perform(get("/api/urls/analytics/abc12345")
                        .principal(() -> "alice"))
                .andExpect(status().isTooManyRequests());
    }

    // ─── Rate-limit headers ──────────────────────────────────────────────────────

    @Test
    void getUserAnalytics_whenNonAdmin_responseIncludesRateLimitHeaders() throws Exception {
        // isAdmin = false but probe is consumed (request is allowed)
        io.github.bucket4j.ConsumptionProbe consumedProbe =
                org.mockito.Mockito.mock(io.github.bucket4j.ConsumptionProbe.class);
        org.mockito.Mockito.when(consumedProbe.isConsumed()).thenReturn(true);
        org.mockito.Mockito.when(consumedProbe.getRemainingTokens()).thenReturn(4L);

        User alice = testUser("alice");
        UrlRateLimitHelper.RateLimitResult allowedWithLimit =
                new UrlRateLimitHelper.RateLimitResult(alice, false, consumedProbe, null);

        when(rateLimitHelper.getRateLimitResult(any(Principal.class), eq(RateLimitEndpoint.ANALYTICS)))
                .thenReturn(allowedWithLimit);

        org.springframework.http.HttpHeaders rlHeaders = new org.springframework.http.HttpHeaders();
        rlHeaders.add("X-RateLimit-Remaining", "4");
        when(rateLimitHelper.buildRateLimitHeaders(any(), any())).thenReturn(rlHeaders);

        LinkAnalyticsResponse response = new LinkAnalyticsResponse();
        response.setTotalClicks(1);
        when(analyticsService.getAnalytics(any(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/urls/analytics/abc12345")
                        .principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "4"));
    }
}
