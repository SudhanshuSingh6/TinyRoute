package com.tinyroute.controller.url;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyroute.infra.ratelimit.RateLimitEndpoint;
import com.tinyroute.dto.url.request.CreateShortUrlRequest;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.User;
import com.tinyroute.exception.GlobalExceptionHandler;
import com.tinyroute.service.url.UrlCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;

import com.tinyroute.exception.ApiException;
import org.springframework.http.HttpStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UrlCreationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UrlCreationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlCreationService urlCreationService;

    @MockitoBean
    private UrlRateLimitHelper rateLimitHelper;

    private User principalUser;

    @BeforeEach
    void rateLimitAllowsRequests() {
        principalUser = testUser();
        when(rateLimitHelper.getRateLimitResult(any(Principal.class), eq(RateLimitEndpoint.SHORTEN)))
                .thenReturn(new UrlRateLimitHelper.RateLimitResult(principalUser, true, null, null));
    }

    @Test
    void createShortUrl_validRequest_returns200AndResponseBody() throws Exception {
        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setShortUrl("abc12345");
        response.setOriginalUrl("https://openai.com");
        when(urlCreationService.createShortUrl(
                eq(principalUser),
                any(CreateShortUrlRequest.class)
        )).thenReturn(response);

        CreateShortUrlRequest request = new CreateShortUrlRequest();
        request.setOriginalUrl("https://openai.com");
        request.setCustomAlias("openai-home");
        request.setTitle("OpenAI");

        mockMvc.perform(post("/api/urls/shorten")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").value("abc12345"))
                .andExpect(jsonPath("$.originalUrl").value("https://openai.com"));
    }

    @Test
    void createShortUrl_invalidRequestBody_returns400() throws Exception {
        mockMvc.perform(post("/api/urls/shorten")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrl_whenNonDeletedExists_returns409Conflict() throws Exception {
        when(urlCreationService.createShortUrl(
                eq(principalUser),
                any(CreateShortUrlRequest.class)
        )).thenThrow(new ApiException(
                HttpStatus.CONFLICT,
                "URL_ALREADY_EXISTS",
                "You already have an active link for this URL."
        ));

        CreateShortUrlRequest request = new CreateShortUrlRequest();
        request.setOriginalUrl("https://openai.com");
        request.setCustomAlias("openai-home");

        mockMvc.perform(post("/api/urls/shorten")
                        .principal(() -> "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("URL_ALREADY_EXISTS"));
    }

    private User testUser() {

        User user = new User();
        user.setId(100L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRole(Role.ROLE_USER);
        return user;
    }
}
