package com.tinyroute.controller;

import com.tinyroute.dtos.ClickEventDTO;
import com.tinyroute.dtos.UrlMappingDTO;
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
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
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

@RestController
@RequestMapping("/api/urls")
@AllArgsConstructor
public class UrlMappingController {

    private UrlMappingService urlMappingService;
    private UserService userService;
    private UrlMappingRepository urlMappingRepository;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

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

    // builds headers from probe — attached to every response
    private HttpHeaders buildRateLimitHeaders(ConsumptionProbe probe, long limit) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(limit));
        headers.add("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        // warning — below 20% remaining
        if (probe.getRemainingTokens() <= (long)(limit * 0.2)) {
            headers.add("X-RateLimit-Warning", "Approaching rate limit");
        }

        // retry-after only on 429 — convert nanoseconds to seconds
        if (!probe.isConsumed()) {
            headers.add("X-RateLimit-Retry-After",
                    String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
        }
        return headers;
    }

    // carries probe + limit + admin flag back to each endpoint
    private record RateLimitResult(ConsumptionProbe probe, long limit, boolean isAdmin) {}

    private RateLimitResult getRateLimitResult(Principal principal, String endpoint) {
        User user = userService.findByUsername(principal.getName());
        String role = user.getRole().name();

        if ("ROLE_ADMIN".equals(role)) {
            return new RateLimitResult(null, Long.MAX_VALUE, true);
        }

        long limit = resolveLimit(role, endpoint);
        Bucket bucket = getBucket(principal.getName(), role, endpoint);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return new RateLimitResult(probe, limit, false);
    }

    @PostMapping("/shorten")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createShortUrl(@RequestBody Map<String, String> request,
                                            Principal principal) {
        RateLimitResult result = getRateLimitResult(principal, "shorten");

        if (!result.isAdmin() && !result.probe().isConsumed()) {
            return ResponseEntity.status(429)
                    .headers(buildRateLimitHeaders(result.probe(), result.limit()))
                    .body("Rate limit exceeded on /shorten. Limit: " + result.limit() + "/hr");
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

        User user = userService.findByUsername(principal.getName());
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

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> toggleUrl(@PathVariable Long id, Principal principal) {
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

    @PostMapping("/myurls")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserUrls(Principal principal) {
        RateLimitResult result = getRateLimitResult(principal, "myurls");

        if (!result.isAdmin() && !result.probe().isConsumed()) {
            return ResponseEntity.status(429)
                    .headers(buildRateLimitHeaders(result.probe(), result.limit()))
                    .body("Rate limit exceeded on /myurls. Limit: " + result.limit() + "/hr");
        }

        User user = userService.findByUsername(principal.getName());
        List<UrlMappingDTO> urls = urlMappingService.getUrlsByUser(user);
        HttpHeaders headers = result.isAdmin()
                ? new HttpHeaders()
                : buildRateLimitHeaders(result.probe(), result.limit());
        return ResponseEntity.ok().headers(headers).body(urls);
    }

    @PostMapping("/analytics/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserAnalytics(
            @PathVariable String shortUrl,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            Principal principal) {
        RateLimitResult result = getRateLimitResult(principal, "analytics");

        if (!result.isAdmin() && !result.probe().isConsumed()) {
            return ResponseEntity.status(429)
                    .headers(buildRateLimitHeaders(result.probe(), result.limit()))
                    .body("Rate limit exceeded on /analytics. Limit: " + result.limit() + "/hr");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime start = LocalDateTime.parse(startDate, formatter);
        LocalDateTime end   = LocalDateTime.parse(endDate, formatter);
        List<ClickEventDTO> clickEventDTOS = urlMappingService.getClickEventsByDate(shortUrl, start, end);
        HttpHeaders headers = result.isAdmin()
                ? new HttpHeaders()
                : buildRateLimitHeaders(result.probe(), result.limit());
        return ResponseEntity.ok().headers(headers).body(clickEventDTOS);
    }

    @GetMapping("/totalClicks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<LocalDate, Long>> getTotalClicksByDate(
            Principal principal,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        User user = userService.findByUsername(principal.getName());
        LocalDate start = LocalDate.parse(startDate, formatter);
        LocalDate end   = LocalDate.parse(endDate, formatter);
        Map<LocalDate, Long> totalClicks = urlMappingService.getTotalClicksByUserAndDate(user, start, end);
        return ResponseEntity.ok(totalClicks);
    }
}