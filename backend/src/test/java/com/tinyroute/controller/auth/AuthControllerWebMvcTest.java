package com.tinyroute.controller.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyroute.auth.controller.AuthController;
import com.tinyroute.auth.dto.LoginRequest;
import com.tinyroute.auth.dto.RegisterRequest;
import com.tinyroute.auth.dto.AuthResponse;
import com.tinyroute.auth.service.RefreshTokenService;
import com.tinyroute.user.entity.Role;
import com.tinyroute.user.entity.User;
import com.tinyroute.exception.AlreadyExistsException;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.handler.GlobalExceptionHandler;
import com.tinyroute.ratelimit.RateLimitHelper;
import com.tinyroute.ratelimit.RateLimitService;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.security.jwt.JwtService;
import com.tinyroute.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private ClientIpService clientIpService;

    @MockitoBean
    private RateLimitHelper rateLimitHelper;

    // ─────────────────────────────────────────────────
    // POST /public/login
    // ─────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200AndTokens() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        AuthResponse response = new AuthResponse("access-token-123", "refresh-token-123");
        when(authService.authenticateUser(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/public/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void login_invalidRequest_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(""); // Invalid: @NotBlank
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/public/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrongpassword");

        when(authService.authenticateUser(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/public/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    // ─────────────────────────────────────────────────
    // POST /public/register
    // ─────────────────────────────────────────────────

    @Test
    void register_validRequest_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("bob_smith");
        request.setEmail("bob@example.com");
        request.setPassword("password123");

        doNothing().when(authService).registerUser(any(RegisterRequest.class));

        mockMvc.perform(post("/api/auth/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void register_usernameConflict_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existinguser");
        request.setEmail("newemail@example.com");
        request.setPassword("password123");

        doThrow(AlreadyExistsException.username())
                .when(authService).registerUser(any(RegisterRequest.class));

        mockMvc.perform(post("/api/auth/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void register_emailConflict_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("existingemail@example.com");
        request.setPassword("password123");

        doThrow(AlreadyExistsException.email())
                .when(authService).registerUser(any(RegisterRequest.class));

        mockMvc.perform(post("/api/auth/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void register_invalidRequest_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ab"); // @Size(min = 3) failed
        request.setEmail("invalid-email"); // @Email failed
        request.setPassword("short"); // @Size(min = 8) failed

        mockMvc.perform(post("/api/auth/public/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────
    // POST /public/refresh
    // ─────────────────────────────────────────────────

    @Test
    void refresh_validToken_returns200AndNewTokens() throws Exception {
        User user = new User();
        user.setId(100L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRole(Role.ROLE_USER);

        RefreshTokenService.RotationResult rotationResult = new RefreshTokenService.RotationResult(user, "new-refresh-token");
        when(jwtService.getRefreshTokenFromCookies(any())).thenReturn("old-refresh-token");
        when(refreshTokenService.rotateToken(eq("old-refresh-token"))).thenReturn(rotationResult);
        when(jwtService.generateAccessToken(any())).thenReturn("new-access-token");

        mockMvc.perform(post("/api/auth/public/refresh")
                        .cookie(new Cookie("refreshToken", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(jwtService.getRefreshTokenFromCookies(any())).thenReturn("invalid-refresh-token");
        when(refreshTokenService.rotateToken(eq("invalid-refresh-token")))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid."));

        mockMvc.perform(post("/api/auth/public/refresh")
                        .cookie(new Cookie("refreshToken", "invalid-refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    // ─────────────────────────────────────────────────
    // POST /public/logout
    // ─────────────────────────────────────────────────

    @Test
    void logout_validToken_returns204() throws Exception {
        doNothing().when(authService).logout(eq("some-refresh-token"));
        when(jwtService.getRefreshTokenFromCookies(any())).thenReturn("some-refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refreshToken", "some-refresh-token")))
                .andExpect(status().isNoContent());
    }

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        when(jwtService.getRefreshTokenFromCookies(any())).thenReturn(null);

        mockMvc.perform(post("/api/auth/public/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("REFRESH_TOKEN_MISSING"));
    }
}
