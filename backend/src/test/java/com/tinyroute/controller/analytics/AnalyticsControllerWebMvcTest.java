package com.tinyroute.controller.analytics;

import com.tinyroute.controller.url.UrlRateLimitHelper;
import com.tinyroute.dto.analytics.response.LinkAnalyticsResponse;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.User;
import com.tinyroute.exception.GlobalExceptionHandler;
import com.tinyroute.service.analytics.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private User principalUser;

    @BeforeEach
    void rateLimitAllowsRequests() {
        principalUser = testUser();
        when(rateLimitHelper.getRateLimitResult(any(Principal.class), eq("analytics")))
                .thenReturn(new UrlRateLimitHelper.RateLimitResult(null, Long.MAX_VALUE, true, principalUser));
    }

    @Test
    void getUserAnalytics_validRequest_returns200() throws Exception {
        LinkAnalyticsResponse response = new LinkAnalyticsResponse();
        response.setTotalClicks(10);
        response.setUniqueClicks(4);
        when(analyticsService.getAnalytics(any(), any(), any(), eq("alice")))
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
    void getUserAnalytics_whenNotFound_returns404() throws Exception {
        when(analyticsService.getAnalytics(any(), any(), any(), eq("alice")))
                .thenReturn(null);

        mockMvc.perform(get("/api/urls/analytics/missing")
                        .principal(() -> "alice")
                        .param("startDate", "2026-03-01T00:00:00")
                        .param("endDate", "2026-03-31T23:59:59"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserAnalytics_invalidDateParam_returns400() throws Exception {
        mockMvc.perform(get("/api/urls/analytics/abc12345")
                        .principal(() -> "alice")
                        .param("startDate", "not-a-date")
                        .param("endDate", "2026-03-31T23:59:59"))
                .andExpect(status().isBadRequest());
    }

    private User testUser() {
        User user = new User();
        user.setId(101L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRole(Role.ROLE_USER);
        return user;
    }
}
