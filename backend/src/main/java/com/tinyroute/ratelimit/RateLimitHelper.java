package com.tinyroute.ratelimit;

import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.exception.ErrorMessages;
import com.tinyroute.exception.RateLimitExceededException;
import com.tinyroute.exception.response.RateLimitErrorResponse;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.user.entity.Role;
import com.tinyroute.user.entity.User;
import com.tinyroute.user.service.UserService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitHelper {

    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final ClientIpService clientIpService;

    public RateLimitResult getRateLimitResult(
            Principal principal,
            RateLimitEndpoint endpoint
    ) {

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
            return new RateLimitResult(
                    user,
                    true,
                    null,
                    null
            );
        }

        RateLimitPlan plan = endpoint.getPlan();

        String key = buildRateLimitKey(
                endpoint,
                user.getId().toString()
        );

        try {

            Bucket bucket =
                    rateLimitService.resolveBucket(
                            key,
                            plan
                    );

            ConsumptionProbe probe =
                    bucket.tryConsumeAndReturnRemaining(1);

            return new RateLimitResult(
                    user,
                    false,
                    probe,
                    plan
            );

        } catch (Exception e) {

            log.warn(
                    "Authenticated rate limiter unavailable for key {}",
                    key,
                    e
            );

            return new RateLimitResult(
                    user,
                    false,
                    null,
                    plan
            );
        }
    }

    public RateLimitResult getPublicRateLimit(
            String ipHash,
            RateLimitEndpoint endpoint
    ) {

        RateLimitPlan plan = endpoint.getPlan();

        String key = buildRateLimitKey(
                endpoint,
                ipHash
        );

        try {

            Bucket bucket =
                    rateLimitService.resolveBucket(
                            key,
                            plan
                    );

            ConsumptionProbe probe =
                    bucket.tryConsumeAndReturnRemaining(1);

            return new RateLimitResult(
                    null,
                    false,
                    probe,
                    plan
            );

        } catch (Exception e) {

            log.warn(
                    "Public rate limiter unavailable for key {}",
                    key,
                    e
            );

            return new RateLimitResult(
                    null,
                    false,
                    null,
                    plan
            );
        }
    }

    public void enforceLimit(
            RateLimitResult result,
            RateLimitEndpoint endpoint
    ) {

        if (
                !result.isAdmin()
                        && result.probe() != null
                        && !result.probe().isConsumed()
        ) {

            throw new RateLimitExceededException(
                    endpoint.getExceededMessage(),
                    extractRetryAfterSeconds(
                            result.probe()
                    )
            );
        }
    }

    public ResponseEntity<RateLimitErrorResponse> applyPublicRateLimit(
            HttpServletRequest request,
            RateLimitEndpoint endpoint,
            String path
    ) {

        try {

            String ip =
                    clientIpService.resolveClientIp(request);

            String ipHash =
                    clientIpService.hashIp(ip);

            RateLimitResult result =
                    getPublicRateLimit(
                            ipHash,
                            endpoint
                    );

            ConsumptionProbe probe =
                    result.probe();

            if (probe == null || probe.isConsumed()) {
                return null;
            }

            long retryAfter =
                    extractRetryAfterSeconds(probe);

            log.warn(
                    "Rate limit exceeded for endpoint '{}' and ipHash '{}'",
                    endpoint.name(),
                    ipHash
            );

            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(
                            HttpHeaders.RETRY_AFTER,
                            String.valueOf(retryAfter)
                    )
                    .header(
                            "X-RateLimit-Limit",
                            String.valueOf(
                                    endpoint.getPlan().getCapacity()
                            )
                    )
                    .header(
                            "X-RateLimit-Remaining",
                            String.valueOf(
                                    probe.getRemainingTokens()
                            )
                    )
                    .body(
                            new RateLimitErrorResponse(
                                    "RATE_LIMIT_EXCEEDED",
                                    path,
                                    endpoint.getPlan().getCapacity(),
                                    probe.getRemainingTokens(),
                                    retryAfter,
                                    endpoint.getExceededMessage()
                            )
                    );

        } catch (Exception e) {

            log.warn(
                    "Public rate limit check failed for endpoint {}",
                    endpoint.name(),
                    e
            );

            return null;
        }
    }

    public HttpHeaders buildRateLimitHeaders(
            ConsumptionProbe probe,
            RateLimitPlan plan
    ) {

        HttpHeaders headers = new HttpHeaders();

        if (probe == null || plan == null) {
            return headers;
        }

        headers.add(
                "X-RateLimit-Limit",
                String.valueOf(plan.getCapacity())
        );

        headers.add(
                "X-RateLimit-Remaining",
                String.valueOf(probe.getRemainingTokens())
        );

        if (!probe.isConsumed()) {

            headers.add(
                    HttpHeaders.RETRY_AFTER,
                    String.valueOf(
                            extractRetryAfterSeconds(probe)
                    )
            );
        }

        return headers;
    }

    public long extractRetryAfterSeconds(
            ConsumptionProbe probe
    ) {

        if (probe == null) {
            return 0;
        }

        return Math.max(
                1,
                probe.getNanosToWaitForRefill()
                        / 1_000_000_000
        );
    }

    private String buildRateLimitKey(
            RateLimitEndpoint endpoint,
            String identifier
    ) {

        return String.format(
                "rate_limit:%s:%s",
                endpoint.name(),
                identifier
        );
    }

    public record RateLimitResult(
            User user,
            boolean isAdmin,
            ConsumptionProbe probe,
            RateLimitPlan plan
    ) {
    }
}