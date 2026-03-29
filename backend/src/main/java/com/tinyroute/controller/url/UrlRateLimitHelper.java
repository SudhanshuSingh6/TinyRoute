package com.tinyroute.controller.url;

import com.tinyroute.dto.error.RateLimitErrorResponse;
import com.tinyroute.entity.User;
import com.tinyroute.service.user.UserService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class UrlRateLimitHelper {

    private final UserService userService;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public record RateLimitResult(ConsumptionProbe probe, long limit, boolean isAdmin, User user) {}

    public RateLimitResult getRateLimitResult(Principal principal, String endpoint) {
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

    public HttpHeaders buildRateLimitHeaders(ConsumptionProbe probe, long limit) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(limit));
        headers.add("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.getRemainingTokens() <= (long) (limit * 0.2)) {
            headers.add("X-RateLimit-Warning", "Approaching rate limit");
        }

        if (!probe.isConsumed()) {
            headers.add("X-RateLimit-Retry-After",
                    String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
        }
        return headers;
    }

    public RateLimitErrorResponse build429Body(ConsumptionProbe probe, long limit, String endpoint) {
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
                case "shorten" -> 100;
                case "analytics" -> 200;
                case "myurls" -> 500;
                default -> 100;
            };
            default -> switch (endpoint) {
                case "shorten" -> 10;
                case "analytics" -> 30;
                case "myurls" -> 100;
                default -> 10;
            };
        };
    }
}
