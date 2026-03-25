package com.tinyroute.controller;

import com.tinyroute.dtos.*;
import com.tinyroute.models.UrlMapping;
import com.tinyroute.models.UrlStatus;
import com.tinyroute.models.User;
import com.tinyroute.repository.UrlMappingRepository;
import com.tinyroute.service.UrlMappingService;
import com.tinyroute.service.UserService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Tag(name = "URL Management", description = "Shorten, manage and track URLs")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/urls")
public class UrlMappingController {

    private final UrlMappingService urlMappingService;
    private final UserService userService;
    private final UrlMappingRepository urlMappingRepository;

    @Value("${frontend.url}")
    private String frontendUrl;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public UrlMappingController(UrlMappingService urlMappingService,
                                UserService userService,
                                UrlMappingRepository urlMappingRepository) {
        this.urlMappingService = urlMappingService;
        this.userService = userService;
        this.urlMappingRepository = urlMappingRepository;
    }

    private Bucket getBucket(String username, String role, String endpoint) {
        String key = username + ":" + endpoint;
        return buckets.computeIfAbsent(key, k -> {
            long limit = resolveLimit(role, endpoint);
            return Bucket.builder()
                    .addLimit(Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofHours(1))))
                    .build();
        });
    }

    private long resolveLimit(String role, String endpoint) {
        return switch (role) {
            case "ROLE_ADMIN" -> Long.MAX_VALUE;
            case "ROLE_PREMIUM" -> switch (endpoint) {
                case "shorten"   -> 100;
                case "analytics" -> 200;
                case "myurls"    -> 500;
                default          -> 100;
            };
            default -> switch (endpoint) {
                case "shorten"   -> 10;
                case "analytics" -> 30;
                case "myurls"    -> 100;
                default          -> 10;
            };
        };
    }

    private HttpHeaders buildRateLimitHeaders(ConsumptionProbe probe, long limit) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(limit));
        headers.add("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        if (probe.getRemainingTokens() <= (long)(limit * 0.2)) {
            headers.add("X-RateLimit-Warning", "Approaching rate limit");
        }
        if (!probe.isConsumed()) {
            headers.add("X-RateLimit-Retry-After",
                    String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
        }
        return headers;
    }

    private RateLimitErrorResponse build429Body(ConsumptionProbe probe, long limit, String endpoint) {
        long retryAfter = probe.getNanosToWaitForRefill() / 1_000_000_000;
        return new RateLimitErrorResponse(
                "RATE_LIMIT_EXCEEDED",
                "/" + endpoint,
                limit,
                probe.getRemainingTokens(),
                retryAfter,
                "You have exceeded the limit of " + limit + " requests/hour on /" + endpoint
        );
    }

    private record RateLimitResult(ConsumptionProbe probe, long limit, boolean isAdmin, User user) {}

    private RateLimitResult getRateLimitResult(Principal principal, String endpoint) {
        User user = userService.findByUsername(principal.getName());
        String role = user.getRole().name();
        if ("ROLE_ADMIN".equals(role)) {
            return new RateLimitResult(null, Long.MAX_VALUE, true, user);
        }
        long limit = resolveLimit(role, endpoint);
        Bucket bucket = getBucket(principal.getName(), role, endpoint);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return new RateLimitResult(probe, limit, false, user);
    }

    @Operation(
            summary = "Shorten a URL",
            description = "Creates a short URL. Supports custom alias, expiry date, click limit, title and visibility. Rate limited per role."
    )
    @ApiResponse(responseCode = "200", description = "URL shortened successfully")
    @ApiResponse(responseCode = "400", description = "Alias already taken or domain blacklisted")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @PostMapping("/shorten")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createShortUrl(@RequestBody Map<String, String> request,
                                            Principal principal) {
        RateLimitResult result = getRateLimitResult(principal, "shorten");
        if (!result.isAdmin() && !result.probe().isConsumed()) {
            return ResponseEntity.status(429)
                    .headers(buildRateLimitHeaders(result.probe(), result.limit()))
                    .body(build429Body(result.probe(), result.limit(), "shorten"));
        }

        String originalUrl  = request.get("originalUrl");
        String customAlias  = request.get("customAlias");
        String expiresAtStr = request.get("expiresAt");
        String maxClicksStr = request.get("maxClicks");
        String title        = request.get("title");
        boolean isPublic    = !"false".equals(request.get("isPublic"));

        LocalDateTime expiresAt = (expiresAtStr != null && !expiresAtStr.isBlank())
                ? LocalDateTime.parse(expiresAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
        Integer maxClicks = (maxClicksStr != null && !maxClicksStr.isBlank())
                ? Integer.parseInt(maxClicksStr) : null;

        User user = result.user();
        try {
            UrlMappingDTO dto = urlMappingService.createShortUrl(
                    originalUrl, customAlias, expiresAt, maxClicks, title, isPublic, user);
            HttpHeaders headers = result.isAdmin()
                    ? new HttpHeaders()
                    : buildRateLimitHeaders(result.probe(), result.limit());
            return ResponseEntity.ok().headers(headers).body(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(
            summary = "Soft delete a URL",
            description = "Marks a URL as deleted. It is never removed from the database so analytics remain intact. Only the owner can delete their own URLs."
    )
    @ApiResponse(responseCode = "204", description = "URL deleted successfully")
    @ApiResponse(responseCode = "403", description = "You don't own this URL")
    @ApiResponse(responseCode = "404", description = "URL not found")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> deleteUrl(
            @Parameter(description = "ID of the URL to delete") @PathVariable Long id,
            Principal principal) {
        try {
            urlMappingService.deleteUrl(id, principal.getName());
            return ResponseEntity.noContent().build();  // 204 — success, no body
        } catch (RuntimeException e) {
            if ("FORBIDDEN".equals(e.getMessage())) {
                return ResponseEntity.status(403).body("You don't own this URL.");
            }
            return ResponseEntity.notFound().build();   // URL not found
        }
    }

    @Operation(
            summary = "Edit destination URL",
            description = "Updates the destination of a short URL. The previous destination is saved to edit history. Only the owner can edit their own URLs."
    )
    @ApiResponse(responseCode = "200", description = "URL updated successfully")
    @ApiResponse(responseCode = "400", description = "Domain is blacklisted")
    @ApiResponse(responseCode = "403", description = "You don't own this URL")
    @ApiResponse(responseCode = "404", description = "URL not found")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> editUrl(
            @Parameter(description = "ID of the URL to edit") @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Principal principal) {
        String newOriginalUrl = request.get("originalUrl");
        if (newOriginalUrl == null || newOriginalUrl.isBlank()) {
            return ResponseEntity.badRequest().body("originalUrl is required.");
        }
        try {
            UrlMappingDTO dto = urlMappingService.editUrl(id, newOriginalUrl, principal.getName());
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            if ("FORBIDDEN".equals(e.getMessage())) {
                return ResponseEntity.status(403).body("You don't own this URL.");
            }
            if (e.getMessage().contains("not allowed")) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Get edit history for a URL",
            description = "Returns all previous destination URLs for a given short URL, newest first. Only the owner can view history."
    )
    @ApiResponse(responseCode = "200", description = "Edit history returned")
    @ApiResponse(responseCode = "403", description = "You don't own this URL")
    @ApiResponse(responseCode = "404", description = "URL not found")
    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getEditHistory(
            @Parameter(description = "ID of the URL") @PathVariable Long id,
            Principal principal) {
        try {
            List<UrlEditHistoryDTO> history = urlMappingService.getEditHistory(id, principal.getName());
            return ResponseEntity.ok(history);
        } catch (RuntimeException e) {
            if ("FORBIDDEN".equals(e.getMessage())) {
                return ResponseEntity.status(403).body("You don't own this URL.");
            }
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Toggle URL active/disabled",
            description = "Switches a URL between ACTIVE and DISABLED status. Only the owner can toggle their own URLs."
    )
    @ApiResponse(responseCode = "200", description = "Status updated successfully")
    @ApiResponse(responseCode = "403", description = "You don't own this URL")
    @ApiResponse(responseCode = "404", description = "URL not found")
    @ApiResponse(responseCode = "400", description = "Cannot toggle URL in current status")
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> toggleUrl(
            @Parameter(description = "ID of the URL to toggle") @PathVariable Long id,
            Principal principal) {
        UrlMapping urlMapping = urlMappingRepository.findById(id).orElse(null);
        if (urlMapping == null) return ResponseEntity.notFound().build();

        if (!urlMapping.getUser().getUsername().equals(principal.getName())) {
            return ResponseEntity.status(403).body("You don't own this URL.");
        }

        if (urlMapping.getStatus() == UrlStatus.ACTIVE) {
            urlMapping.setStatus(UrlStatus.DISABLED);
        } else if (urlMapping.getStatus() == UrlStatus.DISABLED) {
            urlMapping.setStatus(UrlStatus.ACTIVE);
        } else {
            return ResponseEntity.badRequest()
                    .body("Cannot toggle a URL with status: " + urlMapping.getStatus());
        }

        urlMappingRepository.save(urlMapping);
        return ResponseEntity.ok("Status updated to: " + urlMapping.getStatus());
    }

    @Operation(
            summary = "Get all my URLs",
            description = "Returns all shortened URLs created by the authenticated user. Soft-deleted URLs are excluded. Rate limited per role."
    )
    @ApiResponse(responseCode = "200", description = "List of URLs returned")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @GetMapping("/myurls")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserUrls(Principal principal) {
        RateLimitResult result = getRateLimitResult(principal, "myurls");
        if (!result.isAdmin() && !result.probe().isConsumed()) {
            return ResponseEntity.status(429)
                    .headers(buildRateLimitHeaders(result.probe(), result.limit()))
                    .body(build429Body(result.probe(), result.limit(), "myurls"));
        }

        User user = result.user();
        List<UrlMappingDTO> urls = urlMappingService.getUrlsByUser(user);
        HttpHeaders headers = result.isAdmin()
                ? new HttpHeaders()
                : buildRateLimitHeaders(result.probe(), result.limit());
        return ResponseEntity.ok().headers(headers).body(urls);
    }

    @Operation(
            summary = "Get analytics for a specific URL",
            description = "Returns click events grouped by date for a given short URL within a date range. Rate limited per role."
    )
    @ApiResponse(responseCode = "200", description = "Click events returned")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @GetMapping("/analytics/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserAnalytics(
            @Parameter(description = "The short URL code", example = "abc12345")
            @PathVariable String shortUrl,
            @Parameter(description = "Start date in ISO format", example = "2024-01-01T00:00:00")
            @RequestParam("startDate") String startDate,
            @Parameter(description = "End date in ISO format", example = "2024-12-31T23:59:59")
            @RequestParam("endDate") String endDate,
            Principal principal) {
        RateLimitResult result = getRateLimitResult(principal, "analytics");
        if (!result.isAdmin() && !result.probe().isConsumed()) {
            return ResponseEntity.status(429)
                    .headers(buildRateLimitHeaders(result.probe(), result.limit()))
                    .body(build429Body(result.probe(), result.limit(), "analytics"));
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime start = LocalDateTime.parse(startDate, formatter);
        LocalDateTime end   = LocalDateTime.parse(endDate, formatter);
        AnalyticsDTO analytics = urlMappingService.getAnalytics(shortUrl, start, end, principal.getName());
        if (analytics == null) return ResponseEntity.notFound().build();
        HttpHeaders headers = result.isAdmin()
                ? new HttpHeaders()
                : buildRateLimitHeaders(result.probe(), result.limit());
        return ResponseEntity.ok().headers(headers).body(analytics);
    }

    @Operation(
            summary = "Get total clicks across all URLs",
            description = "Returns total click counts grouped by date across all URLs of the authenticated user."
    )
    @ApiResponse(responseCode = "200", description = "Total clicks by date returned")
    @GetMapping("/totalClicks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<LocalDate, Long>> getTotalClicksByDate(
            Principal principal,
            @Parameter(description = "Start date", example = "2024-01-01")
            @RequestParam("startDate") String startDate,
            @Parameter(description = "End date", example = "2024-12-31")
            @RequestParam("endDate") String endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        User user = userService.findByUsername(principal.getName());
        LocalDate start = LocalDate.parse(startDate, formatter);
        LocalDate end   = LocalDate.parse(endDate, formatter);
        Map<LocalDate, Long> totalClicks = urlMappingService.getTotalClicksByUserAndDate(user, start, end);
        return ResponseEntity.ok(totalClicks);
    }

    @Operation(
            summary = "Preview a short URL's destination",
            description = "Scrapes the destination page for title, description and og:image. Public endpoint — no auth required."
    )
    @ApiResponse(responseCode = "200", description = "Preview data returned")
    @ApiResponse(responseCode = "404", description = "Short URL not found or deleted")
    @GetMapping("/{shortUrl}/preview")
    public ResponseEntity<?> getPreview(
            @Parameter(description = "The short URL code", example = "abc12345")
            @PathVariable String shortUrl) {
        UrlPreviewDTO preview = urlMappingService.getPreview(shortUrl);
        if (preview == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(preview);
    }

    @Operation(
            summary = "Generate QR code for a short URL",
            description = "Returns a 250x250 PNG QR code encoding the full short URL. Public endpoint — no auth required."
    )
    @ApiResponse(responseCode = "200", description = "PNG image returned")
    @ApiResponse(responseCode = "404", description = "Short URL not found or deleted")
    @GetMapping("/{shortUrl}/qr")
    public ResponseEntity<byte[]> getQrCode(
            @Parameter(description = "The short URL code", example = "abc12345")
            @PathVariable String shortUrl) {
        String baseUrl = frontendUrl.replaceAll("/$", "");
        byte[] png = urlMappingService.generateQr(shortUrl, baseUrl);
        if (png == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}