package com.tinyroute.auth.controller;

import com.tinyroute.auth.dto.LoginRequest;
import com.tinyroute.auth.dto.RefreshTokenRequest;
import com.tinyroute.auth.dto.RegisterRequest;
import com.tinyroute.auth.dto.JwtAuthenticationResponse;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.RateLimitExceededException;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.infra.ratelimit.RateLimitPlan;
import com.tinyroute.security.UserDetailsImpl;
import com.tinyroute.security.jwt.JwtService;
import com.tinyroute.auth.service.AuthService;
import com.tinyroute.auth.service.RefreshTokenService;
import com.tinyroute.service.ratelimit.RateLimitService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Slf4j
@Tag(
        name = "Auth",
        description = "Register, login, token refresh and logout"
)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String LOGIN_KEY_PREFIX =
            "auth:login:";

    private final AuthService authService;

    private final RefreshTokenService
            refreshTokenService;

    private final JwtService jwtService;

    private final RateLimitService
            rateLimitService;

    private final ClientIpService
            clientIpService;

    @Operation(
            summary = "Login",
            description = """
                    Authenticate user and issue secure
                    HttpOnly cookie-based access and
                    refresh tokens.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "Login successful"
    )
    @ApiResponse(
            responseCode = "400",
            description = "Validation failed"
    )
    @ApiResponse(
            responseCode = "401",
            description = "Invalid credentials"
    )
    @PostMapping("/public/login")
    public ResponseEntity<Void> loginUser(
            @Valid @RequestBody
            LoginRequest loginRequest,

            HttpServletRequest request,

            HttpServletResponse response
    ) {

        enforceAuthRateLimit(
                LOGIN_KEY_PREFIX,
                "login",
                request
        );

        JwtAuthenticationResponse jwtResponse =
                authService.authenticateUser(
                        loginRequest
                );

        ResponseCookie accessCookie =
                ResponseCookie.from(
                                "accessToken",
                                jwtResponse.getToken()
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .sameSite("Lax")
                        .maxAge(
                                Duration.ofMinutes(15)
                        )
                        .build();

        ResponseCookie refreshCookie =
                ResponseCookie.from(
                                "refreshToken",
                                jwtResponse
                                        .getRefreshToken()
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .sameSite("Lax")
                        .maxAge(
                                Duration.ofDays(7)
                        )
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                accessCookie.toString()
        );

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
        );

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Register",
            description = "Create a new user account."
    )
    @ApiResponse(
            responseCode = "201",
            description = "User registered successfully"
    )
    @ApiResponse(
            responseCode = "400",
            description = "Validation failed"
    )
    @ApiResponse(
            responseCode = "409",
            description = "Username or email already taken"
    )
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

        String refreshToken =
                jwtService
                        .getRefreshTokenFromCookies(
                                request
                        );

        if (
                refreshToken == null ||
                        refreshToken.isBlank()
        ) {

            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_MISSING",
                    "Refresh token cookie is missing."
            );
        }

        RefreshTokenService.RotationResult
                rotation =
                refreshTokenService.rotateToken(
                        refreshToken
                );

        UserDetailsImpl userDetails =
                UserDetailsImpl.build(
                        rotation.user()
                );

        String newAccessToken =
                jwtService.generateAccessToken(
                        userDetails
                );

        ResponseCookie accessCookie =
                ResponseCookie.from(
                                "accessToken",
                                newAccessToken
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .sameSite("Lax")
                        .maxAge(
                                Duration.ofMinutes(15)
                        )
                        .build();

        ResponseCookie refreshCookie =
                ResponseCookie.from(
                                "refreshToken",
                                rotation
                                        .newRawRefreshToken()
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .sameSite("Lax")
                        .maxAge(
                                Duration.ofDays(7)
                        )
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                accessCookie.toString()
        );

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
        );

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Logout",
            description = """
                    Revoke refresh token and clear
                    authentication cookies.
                    """
    )
    @ApiResponse(
            responseCode = "204",
            description = "Logged out successfully"
    )
    @PostMapping("/public/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody
            RefreshTokenRequest request,

            HttpServletResponse response
    ) {

        authService.logout(
                request.getRefreshToken()
        );

        ResponseCookie clearAccessCookie =
                ResponseCookie.from(
                                "accessToken",
                                ""
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .sameSite("Lax")
                        .maxAge(0)
                        .build();

        ResponseCookie clearRefreshCookie =
                ResponseCookie.from(
                                "refreshToken",
                                ""
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .sameSite("Lax")
                        .maxAge(0)
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                clearAccessCookie.toString()
        );

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                clearRefreshCookie.toString()
        );

        return ResponseEntity.noContent().build();
    }

    private void enforceAuthRateLimit(
            String keyPrefix,
            String endpoint,
            HttpServletRequest request
    ) {

        try {

            String ip =
                    clientIpService
                            .resolveClientIp(request);

            String ipHash =
                    clientIpService.hashIp(ip);

            String key =
                    keyPrefix + ipHash;

            Bucket bucket =
                    rateLimitService.resolveBucket(
                            key,
                            RateLimitPlan.AUTH
                    );

            ConsumptionProbe probe =
                    bucket.tryConsumeAndReturnRemaining(
                            1
                    );

            if (!probe.isConsumed()) {

                long retryAfter =
                        Math.max(
                                1,
                                probe.getNanosToWaitForRefill()
                                        / 1_000_000_000
                        );

                log.warn(
                        """
                                Auth rate limit exceeded
                                endpoint='{}'
                                ipHash='{}'
                                """,
                        endpoint,
                        ipHash
                );

                throw new RateLimitExceededException(
                        "Too many " +
                                endpoint +
                                " attempts. Please try again in "
                                + retryAfter +
                                " seconds.",
                        retryAfter
                );
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {

            log.warn(
                    """
                            Auth rate limit check failed
                            endpoint='{}'
                            error='{}'
                            """,
                    endpoint,
                    e.getMessage()
            );
        }
    }
}