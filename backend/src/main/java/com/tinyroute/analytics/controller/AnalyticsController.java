package com.tinyroute.analytics.controller;

import com.tinyroute.analytics.dto.AnalyticsQueryRequest;
import com.tinyroute.analytics.dto.LinkAnalyticsResponse;
import com.tinyroute.analytics.dto.LiveAnalyticsResponse;
import com.tinyroute.analytics.service.AnalyticsService;
import com.tinyroute.analytics.service.RedisAnalyticsService;
import com.tinyroute.exception.UrlException;
import com.tinyroute.infra.ratelimit.RateLimitEndpoint;
import com.tinyroute.infra.ratelimit.RateLimitHelper;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.repository.UrlMappingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "URL Analytics", description = "Analytics for short URLs")
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final RedisAnalyticsService redisAnalyticsService;
    private final UrlMappingRepository urlMappingRepository;
    private final RateLimitHelper rateLimitHelper;

    /**
     * Historical analytics from PostgreSQL.
     */
    @Operation(
            summary = "Get historical analytics",
            description = "Returns analytics for a short URL using PostgreSQL historical data."
    )
    @ApiResponse(responseCode = "200", description = "Analytics returned successfully")
    @ApiResponse(responseCode = "400", description = "Invalid date range")
    @ApiResponse(responseCode = "404", description = "URL not found")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @GetMapping("/analytics/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LinkAnalyticsResponse> getUserAnalytics(
            @Parameter(description = "The short URL code", example = "abc12345")
            @PathVariable String shortUrl,
            @Valid @ModelAttribute AnalyticsQueryRequest request,
            Principal principal
    ) {

        RateLimitHelper.RateLimitResult result =
                rateLimitHelper.getRateLimitResult(
                        principal,
                        RateLimitEndpoint.ANALYTICS
                );

        rateLimitHelper.enforceLimit(
                result,
                RateLimitEndpoint.ANALYTICS
        );

        LinkAnalyticsResponse analytics =
                analyticsService.getAnalytics(
                        shortUrl,
                        request,
                        result.user().getUsername()
                );

        HttpHeaders headers = result.isAdmin()
                ? new HttpHeaders()
                : rateLimitHelper.buildRateLimitHeaders(
                result.probe(),
                result.plan()
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(analytics);
    }

    /**
     * Live realtime analytics from Redis.
     */
    @GetMapping("/analytics/live/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get live realtime analytics")
    @ApiResponse(responseCode = "200", description = "Live analytics retrieved")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "URL not found")
    public ResponseEntity<LiveAnalyticsResponse> getLiveAnalytics(
            @PathVariable String shortUrl,
            Principal principal
    ) {

        UrlMapping urlMapping =
                urlMappingRepository
                        .findByShortUrlAndUserUsername(
                                shortUrl,
                                principal.getName()
                        )
                        .orElseThrow(UrlException::notFound);

        LiveAnalyticsResponse response =
                redisAnalyticsService.getLiveAnalytics(
                        urlMapping.getId()
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Queue monitoring endpoint.
     */
    @GetMapping("/admin/analytics/queue-status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get analytics queue status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {

        return ResponseEntity.ok(
                Map.of(
                        "queueSize",
                        redisAnalyticsService.getRawQueueSize(),

                        "timestamp",
                        LocalDateTime.now()
                )
        );
    }
}