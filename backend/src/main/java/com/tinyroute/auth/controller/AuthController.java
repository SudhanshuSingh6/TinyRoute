package com.tinyroute.auth.controller;

import com.tinyroute.auth.dto.AuthResponse;
import com.tinyroute.auth.dto.LoginRequest;
import com.tinyroute.auth.dto.RegisterRequest;
import com.tinyroute.auth.service.AuthService;
import com.tinyroute.auth.service.RefreshTokenService;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.exception.response.ApiErrorResponse;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.ratelimit.RateLimitEndpoint;
import com.tinyroute.ratelimit.RateLimitHelper;
import com.tinyroute.ratelimit.RateLimitService;
import com.tinyroute.security.UserDetailsImpl;
import com.tinyroute.security.jwt.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Slf4j
@Tag(name = "Auth", description = "Register, login, token refresh and logout")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final ClientIpService clientIpService;
    private final RateLimitHelper rateLimitHelper;

    @Value("${app.cookie.secure}")
    private boolean secureCookies;

    @Value("${jwt.expiration}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Operation(summary = "Login", description = "Authenticate user and issue secure HttpOnly cookie-based access and refresh tokens.")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/public/login")
    public ResponseEntity<Void> loginUser(
            @Valid @RequestBody
            LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        ResponseEntity<ApiErrorResponse> limitResponse =
                rateLimitHelper.applyPublicRateLimit(
                        request,
                        RateLimitEndpoint.AUTH,
                        "/api/auth/public/login"
                );
        if (limitResponse != null) {
            return ResponseEntity.status(limitResponse.getStatusCode())
                    .headers(limitResponse.getHeaders())
                    .build();
        }
        AuthResponse jwtResponse = authService.authenticateUser(loginRequest);
        ResponseCookie accessCookie =
                buildAuthCookie("accessToken", jwtResponse.getToken(), accessTokenExpirationMs);
        ResponseCookie refreshCookie =
                buildAuthCookie("refreshToken", jwtResponse.getRefreshToken(), refreshTokenExpirationMs);

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok().build();
    }


    @Operation(summary = "Register", description = "Create a new user account.")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Username or email already taken")
    @PostMapping("/public/register")
    public ResponseEntity<Void> registerUser(
            @Valid @RequestBody
            RegisterRequest registerRequest
    ) {
        authService.registerUser(registerRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

    @PostMapping("/public/refresh")
    public ResponseEntity<Void> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = jwtService.getRefreshTokenFromCookies(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCodes.REFRESH_TOKEN_MISSING,
                    "Refresh token cookie is missing."
            );
        }

        RefreshTokenService.RotationResult rotation = refreshTokenService.rotateToken(refreshToken);
        UserDetailsImpl userDetails = UserDetailsImpl.build(rotation.user());

        String newAccessToken = jwtService.generateAccessToken(userDetails);

        ResponseCookie accessCookie =
                buildAuthCookie("accessToken", newAccessToken, accessTokenExpirationMs);
        ResponseCookie refreshCookie =
                buildAuthCookie("refreshToken", rotation.newRawRefreshToken(), refreshTokenExpirationMs);

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Logout", description = "Revoke refresh token and clear authentication cookies.")
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        String refreshToken = jwtService.getRefreshTokenFromCookies(request);

        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }

        ResponseCookie clearAccessCookie = buildAuthCookie("accessToken", "", 0);
        ResponseCookie clearRefreshCookie = buildAuthCookie("refreshToken", "", 0);

        response.addHeader(HttpHeaders.SET_COOKIE, clearAccessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie.toString());

        return ResponseEntity.noContent().build();
    }

    // Builds an HttpOnly, SameSite=Lax auth cookie whose lifetime mirrors the
    // matching token's configured expiry, so cookie and token never drift.
    // A maxAgeMs of 0 clears the cookie.
    private ResponseCookie buildAuthCookie(String name, String value, long maxAgeMs) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookies)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofMillis(maxAgeMs))
                .build();
    }
}