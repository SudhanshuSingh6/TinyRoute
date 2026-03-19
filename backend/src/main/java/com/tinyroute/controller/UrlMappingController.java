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
import io.github.bucket4j.Refill;
import lombok.AllArgsConstructor;
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

    // key = "username:endpoint" e.g. "john:shorten", "john:analytics"
    // one bucket per user per endpoint
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // BUCKET BUILDER
    // builds a bucket based on role + endpoint

    private Bucket getBucket(String username, String role, String endpoint) {
        String key = username + ":" + endpoint;
        return buckets.computeIfAbsent(key, k -> {
            long limit = resolveLimit(role, endpoint);
            return Bucket.builder()
                    .addLimit(Bandwidth.classic(
                            limit,
                            Refill.greedy(limit, Duration.ofHours(1))
                    ))
                    .build();
        });
    }

    // LIMIT RESOLVER
    // returns the correct limit based on role + endpoint

    private long resolveLimit(String role, String endpoint) {
        return switch (role) {
            case "ROLE_ADMIN" -> Long.MAX_VALUE; // unlimited

            case "ROLE_PREMIUM" -> switch (endpoint) {
                case "shorten"   -> 100;
                case "analytics" -> 200;
                case "myurls"    -> 500;
                default          -> 100;
            };

            default -> switch (endpoint) { // ROLE_USER
                case "shorten"   -> 10;
                case "analytics" -> 30;
                case "myurls"    -> 100;
                default          -> 10;
            };
        };
    }

    // RATE LIMIT CHECK HELPER
    // returns null if allowed, ResponseEntity if blocked

    private ResponseEntity<?> checkRateLimit(Principal principal, String endpoint) {
        User user = userService.findByUsername(principal.getName());
        String role = user.getRole();

        // admin — skip rate limiting entirely
        if ("ROLE_ADMIN".equals(role)) return null;

        Bucket bucket = getBucket(principal.getName(), role, endpoint);
        if (!bucket.tryConsume(1)) {
            long limit = resolveLimit(role, endpoint);
            return ResponseEntity.status(429)
                    .body("Rate limit exceeded. Limit: " + limit + " requests/hour on /" + endpoint);
        }
        return null; // null means allowed — proceed normally
    }

    // POST /shorten
    // ROLE_USER: 10/hr | ROLE_PREMIUM: 100/hr | ROLE_ADMIN: unlimited

    @PostMapping("/shorten")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createShortUrl(@RequestBody Map<String, String> request,
                                            Principal principal) {
        ResponseEntity<?> rateLimitResponse = checkRateLimit(principal, "shorten");
        if (rateLimitResponse != null) return rateLimitResponse;

        String originalUrl = request.get("originalUrl");
        String customAlias = request.get("customAlias");
        String expiresAtStr = request.get("expiresAt");
        String maxClicksStr = request.get("maxClicks");
        String title = request.get("title");
        boolean isPublic = !"false".equals(request.get("isPublic"));

        LocalDateTime expiresAt = (expiresAtStr != null && !expiresAtStr.isBlank())
                ? LocalDateTime.parse(expiresAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;

        Integer maxClicks = (maxClicksStr != null && !maxClicksStr.isBlank())
                ? Integer.parseInt(maxClicksStr)
                : null;

        User user = userService.findByUsername(principal.getName());

        try {
            UrlMappingDTO urlMappingDTO = urlMappingService.createShortUrl(
                    originalUrl, customAlias, expiresAt, maxClicks, title, isPublic, user);
            return ResponseEntity.ok(urlMappingDTO);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PATCH /{id}/toggle
    // no rate limit — lightweight operation

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> toggleUrl(@PathVariable Long id, Principal principal) {
        UrlMapping urlMapping = urlMappingRepository.findById(id).orElse(null);

        if (urlMapping == null) {
            return ResponseEntity.notFound().build();
        }

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

    // POST /myurls
    // ROLE_USER: 100/hr | ROLE_PREMIUM: 500/hr | ROLE_ADMIN: unlimited

    @PostMapping("/myurls")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserUrls(Principal principal) {
        ResponseEntity<?> rateLimitResponse = checkRateLimit(principal, "myurls");
        if (rateLimitResponse != null) return rateLimitResponse;

        User user = userService.findByUsername(principal.getName());
        List<UrlMappingDTO> urls = urlMappingService.getUrlsByUser(user);
        return ResponseEntity.ok(urls);
    }

    // POST /analytics/{shortUrl}
    // ROLE_USER: 30/hr | ROLE_PREMIUM: 200/hr | ROLE_ADMIN: unlimited

    @PostMapping("/analytics/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserAnalytics(
            @PathVariable String shortUrl,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            Principal principal) {
        ResponseEntity<?> rateLimitResponse = checkRateLimit(principal, "analytics");
        if (rateLimitResponse != null) return rateLimitResponse;

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime start = LocalDateTime.parse(startDate, formatter);
        LocalDateTime end = LocalDateTime.parse(endDate, formatter);
        List<ClickEventDTO> clickEventDTOS = urlMappingService.getClickEventsByDate(shortUrl, start, end);
        return ResponseEntity.ok(clickEventDTOS);
    }

    // GET /totalClicks
    // no rate limit — same category as analytics

    @GetMapping("/totalClicks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<LocalDate, Long>> getTotalClicksByDate(
            Principal principal,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        User user = userService.findByUsername(principal.getName());
        LocalDate start = LocalDate.parse(startDate, formatter);
        LocalDate end = LocalDate.parse(endDate, formatter);
        Map<LocalDate, Long> totalClicks = urlMappingService.getTotalClicksByUserAndDate(user, start, end);
        return ResponseEntity.ok(totalClicks);
    }
}