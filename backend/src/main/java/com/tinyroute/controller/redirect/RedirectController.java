package com.tinyroute.controller.redirect;

import com.tinyroute.dto.error.RateLimitErrorResponse;
import com.tinyroute.dto.error.RedirectErrorResponse;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.infra.ratelimit.RateLimitPlan;
import com.tinyroute.service.ratelimit.RateLimitService;
import com.tinyroute.service.redirect.UrlRedirectService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Tag(name = "Redirect", description = "Public short URL redirect — no auth required")
@Slf4j
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private static final DateTimeFormatter READABLE_DATE =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:REDIRECT:";

    private final UrlRedirectService urlRedirectService;
    private final RateLimitService   rateLimitService;
    private final ClientIpService    clientIpService;

    @Operation(
            summary     = "Redirect to original URL",
            description = "Resolves a short URL and redirects. "
                    + "Tracks geo, device, browser, referrer and unique clicks. "
                    + "Rate limited to 60 requests per minute per IP."
    )
    @ApiResponse(responseCode = "302", description = "Redirect to original URL")
    @ApiResponse(responseCode = "404", description = "Short URL not found")
    @ApiResponse(responseCode = "410", description = "URL expired, click limit reached, or disabled")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded — too many requests from this IP")
    @ApiResponse(responseCode = "500", description = "Unexpected redirect failure")
    @GetMapping("/{shortUrl}")
    public ResponseEntity<?> redirect(
            @Parameter(description = "Short URL code", example = "abc12345")
            @PathVariable String shortUrl,
            HttpServletRequest request) {

        ResponseEntity<?> limitResponse = enforceRedirectRateLimit(shortUrl, request);
        if (limitResponse != null) {
            return limitResponse;
        }

        // ── Step 2: resolve and redirect ──────────────────────────────────────
        UrlMapping urlMapping;
        try {
            urlMapping = urlRedirectService.getOriginalUrl(shortUrl, request);
        } catch (Exception e) {
            log.error("Unexpected error resolving short URL '{}': {}", shortUrl, e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    new RedirectErrorResponse(
                            UrlStatus.DISABLED,
                            "Something went wrong. Please try again shortly."
                    )
            );
        }

        if (urlMapping == null) {
            return ResponseEntity.notFound().build();
        }

        if (urlMapping.getStatus() == UrlStatus.DISABLED) {
            return gone(urlMapping, "This link has been disabled by its owner.");
        }

        if (urlMapping.getStatus() == UrlStatus.EXPIRED) {
            return gone(urlMapping,
                    "This link expired on " + urlMapping.getExpiresAt().format(READABLE_DATE));
        }

        if (urlMapping.getStatus() == UrlStatus.CLICK_LIMIT_REACHED) {
            return gone(urlMapping,
                    "This link has reached its maximum click limit of "
                            + urlMapping.getMaxClicks());
        }

        URI destination = toSafeRedirectUri(urlMapping.getOriginalUrl());
        if (destination == null) {
            return gone(urlMapping, "This link has an invalid destination URL.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(destination);
        return ResponseEntity.status(302).headers(headers).build();
    }

    private ResponseEntity<?> enforceRedirectRateLimit(String shortUrl,
                                                       HttpServletRequest request) {
        try {
            String ip     = clientIpService.resolveClientIp(request);
            String ipHash = clientIpService.hashIp(ip);
            String key    = RATE_LIMIT_KEY_PREFIX + ipHash;

            Bucket           bucket = rateLimitService.resolveBucket(key, RateLimitPlan.REDIRECT);
            ConsumptionProbe probe  = bucket.tryConsumeAndReturnRemaining(1);

            if (probe.isConsumed()) {
                return null; // allowed — continue to redirect
            }

            long retryAfter = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);

            log.warn("Redirect rate limit exceeded for IP hash '{}' on shortUrl '{}'",
                    ipHash, shortUrl);

            return ResponseEntity.status(429)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                    .header("X-RateLimit-Limit",
                            String.valueOf(RateLimitPlan.REDIRECT.getCapacity()))
                    .header("X-RateLimit-Remaining",
                            String.valueOf(probe.getRemainingTokens()))
                    .body(new RateLimitErrorResponse(
                            "RATE_LIMIT_EXCEEDED",
                            "/" + shortUrl,
                            RateLimitPlan.REDIRECT.getCapacity(),
                            probe.getRemainingTokens(),
                            retryAfter,
                            "Too many redirect requests. Please try again in "
                                    + retryAfter + " seconds."
                    ));

        } catch (Exception e) {
            log.warn("Redirect rate limit check failed (Redis down?), allowing request: {}",
                    e.getMessage());
            return null;
        }
    }


    private ResponseEntity<RedirectErrorResponse> gone(UrlMapping urlMapping, String message) {
        return ResponseEntity.status(410).body(
                new RedirectErrorResponse(urlMapping.getStatus(), message)
        );
    }

    private URI toSafeRedirectUri(String rawUrl) {
        try {
            URI    uri    = URI.create(rawUrl);
            String scheme = uri.getScheme();
            String host   = uri.getHost();

            if (scheme == null || host == null || host.isBlank()) {
                return null;
            }

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return null;
            }

            return uri;
        } catch (Exception ex) {
            return null;
        }
    }
}