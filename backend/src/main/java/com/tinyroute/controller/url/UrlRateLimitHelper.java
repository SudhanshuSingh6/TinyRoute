package com.tinyroute.controller.url;

import com.tinyroute.infra.ratelimit.RateLimitEndpoint;
import com.tinyroute.infra.ratelimit.RateLimitPlan;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.exception.ErrorMessages;
import com.tinyroute.exception.RateLimitExceededException;
import com.tinyroute.service.ratelimit.RateLimitService;
import com.tinyroute.service.user.UserService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class UrlRateLimitHelper {

    private final UserService userService;
    private final RateLimitService rateLimitService;

    public RateLimitResult getRateLimitResult(Principal principal, RateLimitEndpoint endpoint) {
        if (principal == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCodes.AUTHENTICATION_FAILED,
                    ErrorMessages.AUTHENTICATION_FAILED
            );
        }

        User user = userService.findByUsername(principal.getName());
        boolean isAdmin = user.getRole() == Role.ROLE_ADMIN;

        if (isAdmin) {
            return new RateLimitResult(user, true, null, null);
        }

        RateLimitPlan plan = endpoint.getPlan();
        String key = buildRateLimitKey(endpoint, user.getId());

        Bucket bucket = rateLimitService.resolveBucket(key, plan);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        return new RateLimitResult(user, false, probe, plan);
    }

    public void enforceLimit(RateLimitResult result, RateLimitEndpoint endpoint) {
        if (!result.isAdmin() && result.probe() != null && !result.probe().isConsumed()) {
            throw new RateLimitExceededException(
                    endpoint.getExceededMessage(),
                    extractRetryAfterSeconds(result.probe())
            );
        }
    }

    private String buildRateLimitKey(RateLimitEndpoint endpoint, Long userId) {
        return "rate_limit:" + endpoint.name() + ":" + userId;
    }

    public HttpHeaders buildRateLimitHeaders(ConsumptionProbe probe, RateLimitPlan plan) {
        HttpHeaders headers = new HttpHeaders();

        if (probe == null || plan == null) {
            return headers;
        }

        headers.add("X-RateLimit-Limit", String.valueOf(plan.getCapacity()));
        headers.add("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(extractRetryAfterSeconds(probe)));

        return headers;
    }

    public long extractRetryAfterSeconds(ConsumptionProbe probe) {
        if (probe == null) {
            return 0;
        }
        return Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
    }

    public record RateLimitResult(
            User user,
            boolean isAdmin,
            ConsumptionProbe probe,
            RateLimitPlan plan
    ) {}
}