package com.tinyroute.controller.analytics;

import com.tinyroute.controller.url.UrlRateLimitHelper;
import com.tinyroute.dto.analytics.request.AnalyticsQueryRequest;
import com.tinyroute.dto.analytics.response.LinkAnalyticsResponse;
import com.tinyroute.service.analytics.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Tag(name = "URL Analytics", description = "Analytics for short URLs")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UrlRateLimitHelper rateLimitHelper;

    @Operation(
            summary = "Get analytics for a specific URL",
            description = "Returns analytics for a given short URL. If no date range is provided, analytics are returned from link creation date to now."
    )
    @ApiResponse(responseCode = "200", description = "Analytics returned successfully")
    @ApiResponse(responseCode = "400", description = "Invalid date range")
    @ApiResponse(responseCode = "404", description = "Link not found")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @GetMapping("/analytics/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserAnalytics(
            @Parameter(description = "The short URL code", example = "abc12345")
            @PathVariable String shortUrl,
            @Valid @ModelAttribute AnalyticsQueryRequest request,
            Principal principal) {


        UrlRateLimitHelper.RateLimitResult result =
                rateLimitHelper.getRateLimitResult(principal, "analytics");

        if (!result.isAdmin() && !result.probe().isConsumed()) {
            return ResponseEntity.status(429)
                    .headers(rateLimitHelper.buildRateLimitHeaders(result.probe(), result.limit()))
                    .body(rateLimitHelper.build429Body(result.probe(), result.limit(), "analytics"));
        }

        LinkAnalyticsResponse analytics = analyticsService.getAnalytics(
                shortUrl,
                request,
                principal.getName()
        );

        HttpHeaders headers = result.isAdmin()
                ? new HttpHeaders()
                : rateLimitHelper.buildRateLimitHeaders(result.probe(), result.limit());

        return ResponseEntity.ok().headers(headers).body(analytics);
    }
}