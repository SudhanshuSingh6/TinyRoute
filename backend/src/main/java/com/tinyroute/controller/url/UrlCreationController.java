package com.tinyroute.controller.url;

import com.tinyroute.dto.url.request.CreateShortUrlRequest;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.service.url.UrlCreationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Tag(name = "URL Creation", description = "Create short URLs")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class UrlCreationController {

        private final UrlCreationService urlCreationService;
        private final UrlRateLimitHelper rateLimitHelper;

        @Operation(summary = "Shorten a URL", description = "Creates a short URL. Supports custom alias, expiry date, click limit and title. Rate limited per role.")
        @ApiResponse(responseCode = "200", description = "URL shortened successfully")
        @ApiResponse(responseCode = "400", description = "Alias already taken or domain blacklisted")
        @ApiResponse(responseCode = "401", description = "Authenticated user could not be resolved")
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        @PostMapping("/shorten")
        @PreAuthorize("hasRole('USER')")
        public ResponseEntity<?> createShortUrl(
                        @Valid @RequestBody CreateShortUrlRequest request,
                        Principal principal) {
                UrlRateLimitHelper.RateLimitResult result = rateLimitHelper.getRateLimitResult(principal, "shorten");

                if (result.user() == null) {
                        return ResponseEntity.status(401).body("Authenticated user could not be resolved");
                }

                if (!result.isAdmin() && !result.probe().isConsumed()) {
                        return ResponseEntity.status(429)
                                        .headers(rateLimitHelper.buildRateLimitHeaders(result.probe(), result.limit()))
                                        .body(rateLimitHelper.build429Body(result.probe(), result.limit(), "shorten"));
                }

                UrlDetailsResponse dto = urlCreationService.createShortUrl(
                                request.getOriginalUrl(),
                                request.getCustomAlias(),
                                request.getExpiresAt(),
                                request.getTitle(),
                                result.user());

                HttpHeaders headers = result.isAdmin()
                                ? new HttpHeaders()
                                : rateLimitHelper.buildRateLimitHeaders(result.probe(), result.limit());

                return ResponseEntity.ok().headers(headers).body(dto);
        }
}