package com.tinyroute.controller.url;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyroute.dto.url.request.UpdateExpiryRequest;
import com.tinyroute.dto.url.request.UpdateShortUrlRequest;
import com.tinyroute.dto.url.response.EditHistoryDTO;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.GlobalExceptionHandler;
import com.tinyroute.exception.LinkException;
import com.tinyroute.repository.url.UrlMappingRepository;
import com.tinyroute.service.analytics.AnalyticsService;
import com.tinyroute.service.url.UrlManagementService;
import com.tinyroute.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private UrlMappingRepository urlMappingRepository;

    @MockitoBean
    private UrlRateLimitHelper rateLimitHelper;

    // ─────────────────────────────────────────────────
    // DELETE /{id}
    // ─────────────────────────────────────────────────

    @Test
    void deleteUrl_success_returns204() throws Exception {
        doNothing().when(urlManagementService).deleteUrl(eq(1L), eq("alice"));

        mockMvc.perform(delete("/api/urls/1")
                        .principal(() -> "alice"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUrl_notFound_returns404() throws Exception {
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "URL_NOT_FOUND", "Not found"))
                .when(urlManagementService).deleteUrl(eq(99L), eq("alice"));

        mockMvc.perform(delete("/api/urls/99")
                        .principal(() -> "alice"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUrl_notOwner_returns403() throws Exception {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't own this URL."))
                .when(urlManagementService).deleteUrl(eq(2L), eq("alice"));

        mockMvc.perform(delete("/api/urls/2")
                        .principal(() -> "alice"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────
    // PUT /{id} — edit destination URL
    // ─────────────────────────────────────────────────

    @Test
    void editUrl_validRequest_returns200() throws Exception {
        User user = testUser();
        when(userService.findByUsername("alice")).thenReturn(user);

        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setShortUrl("abc12345");
        response.setOriginalUrl("https://new-destination.com");
        when(urlManagementService.editUrl(eq(1L), eq("https://new-destination.com"), eq(user)))
                .thenReturn(response);

        UpdateShortUrlRequest request = new UpdateShortUrlRequest();
        request.setOriginalUrl("https://new-destination.com");

        mockMvc.perform(put("/api/urls/1")
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
                .when(urlManagementService).editUrl(eq(2L), any(), eq(user));

        UpdateShortUrlRequest request = new UpdateShortUrlRequest();
        request.setOriginalUrl("https://new-destination.com");

        mockMvc.perform(put("/api/urls/2")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void editUrl_destinationAlreadyExists_returns409() throws Exception {
        User user = testUser();
        when(userService.findByUsername("alice")).thenReturn(user);
        when(urlManagementService.editUrl(eq(1L), eq("https://dup.com"), eq(user)))
                .thenThrow(new ApiException(
                        HttpStatus.CONFLICT,
                        "URL_ALREADY_EXISTS",
                        "A link for this destination already exists. Use the existing link instead."));

        UpdateShortUrlRequest request = new UpdateShortUrlRequest();
        request.setOriginalUrl("https://dup.com");

        mockMvc.perform(put("/api/urls/1")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("URL_ALREADY_EXISTS"));
    }


    @Test
    void editUrl_invalidBody_returns400() throws Exception {
        // Missing originalUrl: @NotBlank on UpdateShortUrlRequest ({} deserializes to null)
        mockMvc.perform(put("/api/urls/1")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────
    // GET /{id}/history
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

        when(urlManagementService.getEditHistory(eq(1L), eq("alice")))
                .thenReturn(List.of(entry1, entry2));

        mockMvc.perform(get("/api/urls/1/history")
                        .principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oldUrl").value("https://old-url.com"))
                .andExpect(jsonPath("$[1].oldUrl").value("https://even-older-url.com"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getEditHistory_notOwner_returns403() throws Exception {
        when(urlManagementService.getEditHistory(eq(2L), eq("alice")))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't own this URL."));

        mockMvc.perform(get("/api/urls/2/history")
                        .principal(() -> "alice"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEditHistory_notFound_returns404() throws Exception {
        when(urlManagementService.getEditHistory(eq(99L), eq("alice")))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "URL_NOT_FOUND", "Not found"));

        mockMvc.perform(get("/api/urls/99/history")
                        .principal(() -> "alice"))
                .andExpect(status().isNotFound());
    }


    // ─────────────────────────────────────────────────
    // PATCH /{id}/toggle
    // ─────────────────────────────────────────────────

    static Stream<Arguments> toggleFromActiveOrDisabled() {
        return Stream.of(
                Arguments.of(UrlStatus.ACTIVE, UrlStatus.DISABLED),
                Arguments.of(UrlStatus.DISABLED, UrlStatus.ACTIVE)
        );
    }

    @ParameterizedTest
    @MethodSource("toggleFromActiveOrDisabled")
    void toggleUrl_activeDisabledRoundTrip_returns200(UrlStatus initial, UrlStatus expectedAfterToggle)
            throws Exception {
        User owner = testUser();
        UrlMapping mapping = activeMapping(owner);
        mapping.setStatus(initial);

        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(patch("/api/urls/1/toggle")
                        .principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedAfterToggle.name()));

        assertEquals(expectedAfterToggle, mapping.getStatus());
    }

    @Test
    void toggleUrl_notFound_returns404() throws Exception {
        when(urlMappingRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/urls/99/toggle")
                        .principal(() -> "alice"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("LINK_NOT_FOUND"));
    }

    @Test
    void toggleUrl_notOwner_returns403() throws Exception {
        User someone = new User();
        someone.setUsername("bob");
        UrlMapping mapping = activeMapping(someone);

        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));

        mockMvc.perform(patch("/api/urls/1/toggle")
                        .principal(() -> "alice"))  // alice ≠ bob
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("LINK_ACCESS_DENIED"));
    }


    @Test
    void toggleUrl_expiredStatus_returns400() throws Exception {
        User owner = testUser();
        UrlMapping mapping = activeMapping(owner);
        mapping.setStatus(UrlStatus.EXPIRED);

        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));

        mockMvc.perform(patch("/api/urls/1/toggle")
                        .principal(() -> "alice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("URL_TOGGLE_INVALID"));
    }

    // ─────────────────────────────────────────────────
    // PATCH /{id}/expiry
    // ─────────────────────────────────────────────────

    @Test
    void updateExpiry_validFutureDate_returns200() throws Exception {
        User user = testUser();
        when(userService.findByUsername("alice")).thenReturn(user);

        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setShortUrl("abc12345");
        response.setExpiresAt(LocalDateTime.of(2027, 1, 1, 0, 0));
        when(urlManagementService.updateExpiry(eq(1L), any(), eq(user))).thenReturn(response);

        UpdateExpiryRequest request = new UpdateExpiryRequest();
        request.setExpiresAt(LocalDateTime.of(2027, 1, 1, 0, 0));

        mockMvc.perform(patch("/api/urls/1/expiry")
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

        mockMvc.perform(patch("/api/urls/1/expiry")
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

    private UrlMapping activeMapping(User owner) {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(1L);
        mapping.setShortUrl("abc12345");
        mapping.setOriginalUrl("https://openai.com");
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setUser(owner);
        return mapping;
    }
}
