package com.tinyroute.controller.url;

import com.tinyroute.entity.Role;
import com.tinyroute.entity.User;
import com.tinyroute.common.ratelimit.RateLimitPlan;
import com.tinyroute.service.ratelimit.RateLimitService;
import com.tinyroute.service.user.UserService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UrlRateLimitHelper {

    private final UserService userService;
    private final RateLimitService rateLimitService;

    public RateLimitResult getRateLimitResult(Principal principal, String endpoint) {
        User user = userService.findByUsername(principal.getName());

        boolean isAdmin = user.getRole() == Role.ROLE_ADMIN;

        if (isAdmin) {
            return new RateLimitResult(user, true, null, null);
        }

        RateLimitPlan plan = resolvePlan(endpoint);
        String key = "rate_limit:" + endpoint + ":" + user.getId();

        Bucket bucket = rateLimitService.resolveBucket(key, plan);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        return new RateLimitResult(user, false, probe, plan);
    }

    private RateLimitPlan resolvePlan(String endpoint) {
        return switch (endpoint) {
            case "analytics" -> RateLimitPlan.ANALYTICS;
            case "myurls" -> RateLimitPlan.MY_URLS;
            case "url_management" -> RateLimitPlan.URL_MANAGEMENT;
            case "auth" -> RateLimitPlan.AUTH;
            default -> RateLimitPlan.DEFAULT;
        };
    }

    public HttpHeaders buildRateLimitHeaders(ConsumptionProbe probe, RateLimitPlan plan) {
        HttpHeaders headers = new HttpHeaders();

        if (probe == null || plan == null) {
            return headers;
        }

        headers.add("X-RateLimit-Limit", String.valueOf(plan.getCapacity()));
        headers.add("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        headers.add("X-RateLimit-Retry-After-Seconds",
                String.valueOf(Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000)));

        return headers;
    }

    public Map<String, Object> build429Body(ConsumptionProbe probe, RateLimitPlan plan, String endpoint) {
        return Map.of(
                "status", 429,
                "error", "RATE_LIMIT_EXCEEDED",
                "message", "Too many requests for " + endpoint + ". Please try again later.",
                "limit", plan != null ? plan.getCapacity() : 0,
                "remaining", probe != null ? probe.getRemainingTokens() : 0,
                "retryAfterSeconds", probe != null
                        ? Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000)
                        : 0
        );
    }

    public record RateLimitResult(
            User user,
            boolean isAdmin,
            ConsumptionProbe probe,
            RateLimitPlan limit
    ) {}
}