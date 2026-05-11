package com.tinyroute.controller.url;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyroute.dto.url.request.UpdateExpiryRequest;
import com.tinyroute.dto.url.request.UpdateShortUrlRequest;
import com.tinyroute.dto.url.response.EditHistoryDTO;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.GlobalExceptionHandler;
import com.tinyroute.service.analytics.AnalyticsService;
import com.tinyroute.service.url.UrlManagementService;
import com.tinyroute.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UrlManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UrlManagementControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlManagementService urlManagementService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UrlRateLimitHelper rateLimitHelper;

    @BeforeEach
    void setUpRateLimit() {
        when(rateLimitHelper.getRateLimitResult(any(java.security.Principal.class), any()))
                .thenReturn(new UrlRateLimitHelper.RateLimitResult(testUser(), true, null, null));
        when(userService.findByUsername("alice")).thenReturn(testUser());
    }

    // ─────────────────────────────────────────────────
    // DELETE /{shortUrl}
    // ─────────────────────────────────────────────────

    @Test
    void deleteUrl_success_returns204() throws Exception {
        doNothing().when(urlManagementService).deleteUrl(eq("abc12345"), eq(100L));

        mockMvc.perform(delete("/api/urls/abc12345")
                        .principal(() -> "alice"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUrl_notFound_returns404() throws Exception {
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "URL_NOT_FOUND", "Not found"))
                .when(urlManagementService).deleteUrl(eq("missing99"), eq(100L));

        mockMvc.perform(delete("/api/urls/missing99")
                        .principal(() -> "alice"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUrl_notOwner_returns403() throws Exception {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't own this URL."))
                .when(urlManagementService).deleteUrl(eq("alice-own"), eq(100L));

        mockMvc.perform(delete("/api/urls/alice-own")
                        .principal(() -> "alice"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────
    // PUT /{shortUrl} — edit destination URL
    // ─────────────────────────────────────────────────

    @Test
    void editUrl_validRequest_returns200() throws Exception {
        User user = testUser();
        when(userService.findByUsername("alice")).thenReturn(user);

        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setShortUrl("abc12345");
        response.setOriginalUrl("https://new-destination.com");
        when(urlManagementService.editUrl(eq("abc12345"), any(UpdateShortUrlRequest.class), eq(user.getId())))
                .thenReturn(response);

        UpdateShortUrlRequest request = new UpdateShortUrlRequest();
        request.setOriginalUrl("https://new-destination.com");

        mockMvc.perform(put("/api/urls/abc12345")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").value("abc12345"))
                .andExpect(jsonPath("$.originalUrl").value("https://new-destination.com"));
    }

    @Test
    void editUrl_notOwner_returns403() throws Exception {
        User user = testUser();
        when(userService.findByUsername("alice")).thenReturn(user);
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't own this URL."))
                .when(urlManagementService).editUrl(eq("abc12345"), any(UpdateShortUrlRequest.class), eq(user.getId()));

        UpdateShortUrlRequest request = new UpdateShortUrlRequest();
        request.setOriginalUrl("https://new-destination.com");

        mockMvc.perform(put("/api/urls/abc12345")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void editUrl_destinationAlreadyExists_returns409() throws Exception {
        User user = testUser();
        when(userService.findByUsername("alice")).thenReturn(user);
        when(urlManagementService.editUrl(eq("abc12345"), any(UpdateShortUrlRequest.class), eq(user.getId())))
                .thenThrow(new ApiException(
                        HttpStatus.CONFLICT,
                        "URL_ALREADY_EXISTS",
                        "A link for this destination already exists. Use the existing link instead."));

        UpdateShortUrlRequest request = new UpdateShortUrlRequest();
        request.setOriginalUrl("https://dup.com");

        mockMvc.perform(put("/api/urls/abc12345")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("URL_ALREADY_EXISTS"));
    }


    @Test
    void editUrl_invalidBody_returns400() throws Exception {
        // Missing originalUrl: @NotBlank on UpdateShortUrlRequest ({} deserializes to null)
        mockMvc.perform(put("/api/urls/abc12345")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────
    // GET /{shortUrl}/history
    // ─────────────────────────────────────────────────

    @Test
    void getEditHistory_validOwner_returns200WithHistory() throws Exception {
        EditHistoryDTO entry1 = new EditHistoryDTO();
        entry1.setId(10L);
        entry1.setOldUrl("https://old-url.com");
        entry1.setChangedAt(LocalDateTime.of(2026, 1, 1, 12, 0));

        EditHistoryDTO entry2 = new EditHistoryDTO();
        entry2.setId(11L);
        entry2.setOldUrl("https://even-older-url.com");
        entry2.setChangedAt(LocalDateTime.of(2025, 12, 1, 8, 0));

        when(urlManagementService.getEditHistory(eq("abc12345"), eq(100L)))
                .thenReturn(List.of(entry1, entry2));

        mockMvc.perform(get("/api/urls/abc12345/history")
                        .principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oldUrl").value("https://old-url.com"))
                .andExpect(jsonPath("$[1].oldUrl").value("https://even-older-url.com"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getEditHistory_notOwner_returns403() throws Exception {
        when(urlManagementService.getEditHistory(eq("abc12345"), eq(100L)))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't own this URL."));

        mockMvc.perform(get("/api/urls/abc12345/history")
                        .principal(() -> "alice"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEditHistory_notFound_returns404() throws Exception {
        when(urlManagementService.getEditHistory(eq("missing99"), eq(100L)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "URL_NOT_FOUND", "Not found"));

        mockMvc.perform(get("/api/urls/missing99/history")
                        .principal(() -> "alice"))
                .andExpect(status().isNotFound());
    }


    // ─────────────────────────────────────────────────
    // PATCH /{shortUrl}/disable
    // ─────────────────────────────────────────────────

    @Test
    void disableUrl_success_returns200() throws Exception {
        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setShortUrl("abc12345");
        response.setStatus(UrlStatus.DISABLED);
        when(urlManagementService.disableUrl("abc12345", 100L)).thenReturn(response);

        mockMvc.perform(patch("/api/urls/abc12345/disable")
                        .principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").value("abc12345"))
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void disableUrl_invalidState_returns400() throws Exception {
        when(urlManagementService.disableUrl("abc12345", 100L))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "URL_DISABLE_INVALID", "Only ACTIVE links can be disabled."));

        mockMvc.perform(patch("/api/urls/abc12345/disable")
                        .principal(() -> "alice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("URL_DISABLE_INVALID"));
    }

    // ─────────────────────────────────────────────────
    // PATCH /{shortUrl}/enable
    // ─────────────────────────────────────────────────
    @Test
    void enableUrl_success_returns200() throws Exception {
        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setShortUrl("abc12345");
        response.setStatus(UrlStatus.ACTIVE);
        when(urlManagementService.enableUrl("abc12345", 100L)).thenReturn(response);

        mockMvc.perform(patch("/api/urls/abc12345/enable")
                        .principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").value("abc12345"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void enableUrl_expired_returns400() throws Exception {
        when(urlManagementService.enableUrl("abc12345", 100L))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "URL_EXPIRED", "Cannot enable an expired URL."));

        mockMvc.perform(patch("/api/urls/abc12345/enable")
                        .principal(() -> "alice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("URL_EXPIRED"));
    }

    // ─────────────────────────────────────────────────
    // PATCH /{shortUrl}/expiry
    // ─────────────────────────────────────────────────

    @Test
    void updateExpiry_validFutureDate_returns200() throws Exception {
        User user = testUser();
        when(userService.findByUsername("alice")).thenReturn(user);

        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setShortUrl("abc12345");
        response.setExpiresAt(LocalDateTime.of(2027, 1, 1, 0, 0));
        when(urlManagementService.updateExpiry(eq("abc12345"), any(), eq(user.getId()))).thenReturn(response);

        UpdateExpiryRequest request = new UpdateExpiryRequest();
        request.setExpiresAt(LocalDateTime.of(2027, 1, 1, 0, 0));

        mockMvc.perform(patch("/api/urls/abc12345/expiry")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void updateExpiry_pastDate_returns400() throws Exception {
        UpdateExpiryRequest request = new UpdateExpiryRequest();
        request.setExpiresAt(LocalDateTime.of(2020, 1, 1, 0, 0));

        mockMvc.perform(patch("/api/urls/abc12345/expiry")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }


    // ─────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────

    private User testUser() {
        User user = new User();
        user.setId(100L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRole(Role.ROLE_USER);
        return user;
    }
}
