package com.tinyroute.controller.url;

import com.tinyroute.dto.url.request.UpdateExpiryRequest;
import com.tinyroute.dto.url.request.UpdateShortUrlRequest;
import com.tinyroute.dto.url.response.EditHistoryDTO;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.User;
import com.tinyroute.service.analytics.AnalyticsService;
import com.tinyroute.service.url.UrlManagementService;
import com.tinyroute.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "URL Management", description = "Manage your short URLs")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class UrlManagementController {

    private final UrlManagementService urlManagementService;
    private final AnalyticsService analyticsService;
    private final UserService userService;
    private final UrlRateLimitHelper rateLimitHelper;

    @Operation(summary = "Get all my URLs")
    @ApiResponse(responseCode = "200", description = "List of URLs returned")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserUrls(Principal principal) {
        UrlRateLimitHelper.RateLimitResult result =
                rateLimitHelper.getRateLimitResult(principal, "myurls");

        if (!result.isAdmin() && !result.probe().isConsumed()) {
            return ResponseEntity.status(429)
                    .headers(rateLimitHelper.buildRateLimitHeaders(result.probe(), result.limit()))
                    .body(rateLimitHelper.build429Body(result.probe(), result.limit(), "myurls"));
        }

        List<UrlDetailsResponse> urls = urlManagementService.getUrlsByUser(result.user());

        HttpHeaders headers = result.isAdmin()
                ? new HttpHeaders()
                : rateLimitHelper.buildRateLimitHeaders(result.probe(), result.limit());

        return ResponseEntity.ok().headers(headers).body(urls);
    }

    @Operation(summary = "Get details of a URL")
    @ApiResponse(responseCode = "200", description = "URL details returned")
    @ApiResponse(responseCode = "404", description = "URL not found")
    @GetMapping("/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UrlDetailsResponse> getUrlDetails(
            @Parameter(description = "Short URL code") @PathVariable String shortUrl,
            Principal principal) {
        return ResponseEntity.ok(
                urlManagementService.getUrlDetails(shortUrl, principal.getName())
        );
    }

    @Operation(summary = "Edit destination URL and title")
    @ApiResponse(responseCode = "200", description = "URL updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid or blacklisted destination URL")
    @ApiResponse(responseCode = "404", description = "URL not found")
    @PutMapping("/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UrlDetailsResponse> editUrl(
            @Parameter(description = "Short URL code") @PathVariable String shortUrl,
            @Valid @RequestBody UpdateShortUrlRequest request,
            Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return ResponseEntity.ok(
                urlManagementService.editUrl(shortUrl, request, user)
        );
    }

    @Operation(summary = "Update URL expiry")
    @ApiResponse(responseCode = "200", description = "Expiry updated successfully")
    @PatchMapping("/{shortUrl}/expiry")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UrlDetailsResponse> updateExpiry(
            @Parameter(description = "Short URL code") @PathVariable String shortUrl,
            @Valid @RequestBody UpdateExpiryRequest request,
            Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return ResponseEntity.ok(
                urlManagementService.updateExpiry(shortUrl, request.getExpiresAt(), user)
        );
    }

    @Operation(summary = "Disable a URL")
    @ApiResponse(responseCode = "200", description = "URL disabled successfully")
    @PatchMapping("/{shortUrl}/disable")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UrlDetailsResponse> disableUrl(
            @Parameter(description = "Short URL code") @PathVariable String shortUrl,
            Principal principal) {
        return ResponseEntity.ok(
                urlManagementService.disableUrl(shortUrl, principal.getName())
        );
    }

    @Operation(summary = "Enable a URL")
    @ApiResponse(responseCode = "200", description = "URL enabled successfully")
    @PatchMapping("/{shortUrl}/enable")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UrlDetailsResponse> enableUrl(
            @Parameter(description = "Short URL code") @PathVariable String shortUrl,
            Principal principal) {
        return ResponseEntity.ok(
                urlManagementService.enableUrl(shortUrl, principal.getName())
        );
    }

    @Operation(summary = "Get edit history for a URL")
    @ApiResponse(responseCode = "200", description = "Edit history returned")
    @GetMapping("/{shortUrl}/history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<EditHistoryDTO>> getEditHistory(
            @Parameter(description = "Short URL code") @PathVariable String shortUrl,
            Principal principal) {
        return ResponseEntity.ok(
                urlManagementService.getEditHistory(shortUrl, principal.getName())
        );
    }

    @Operation(
            summary = "Delete a URL",
            description = "Permanently deletes a URL and all its associated analytics."
    )
    @ApiResponse(responseCode = "204", description = "URL deleted successfully")
    @DeleteMapping("/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteUrl(
            @Parameter(description = "Short URL code") @PathVariable String shortUrl,
            Principal principal) {
        urlManagementService.deleteUrl(shortUrl, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Get total clicks across all URLs",
            description = "Returns total click counts grouped by date across all URLs of the authenticated user."
    )
    @ApiResponse(responseCode = "200", description = "Total clicks by date returned")
    @GetMapping("/total-clicks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<LocalDate, Long>> getTotalClicksByDate(
            Principal principal,
            @Parameter(description = "Start date", example = "2024-01-01")
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @Parameter(description = "End date", example = "2024-12-31")
            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {
        User user = userService.findByUsername(principal.getName());
        Map<LocalDate, Long> totalClicks =
                analyticsService.getTotalClicksByUserAndDate(user, startDate, endDate);

        return ResponseEntity.ok(totalClicks);
    }
}