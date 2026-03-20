package com.tinyroute.controller;

import com.tinyroute.dtos.RedirectErrorResponse;
import com.tinyroute.models.UrlMapping;
import com.tinyroute.models.UrlStatus;
import com.tinyroute.service.UrlMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Redirect", description = "Public short URL redirect — no auth required")
@RestController
@AllArgsConstructor
public class RedirectController {

    private UrlMappingService urlMappingService;

    @Operation(
            summary = "Redirect to original URL",
            description = "Resolves a short URL and redirects. Tracks geo, device, browser, referrer and unique clicks."
    )
    @ApiResponse(responseCode = "302", description = "Redirect to original URL")
    @ApiResponse(responseCode = "404", description = "Short URL not found")
    @ApiResponse(responseCode = "410", description = "URL expired, click limit reached, or disabled")
    @GetMapping("/{shortUrl}")
    public ResponseEntity<?> redirect(
            @Parameter(description = "Short URL code", example = "abc12345")
            @PathVariable String shortUrl,
            HttpServletRequest request) {   // NEW — passed to service for analytics

        UrlMapping urlMapping = urlMappingService.getOriginalUrl(shortUrl, request);

        if (urlMapping == null) {
            return ResponseEntity.notFound().build();
        }

        if (urlMapping.getStatus() == UrlStatus.EXPIRED) {
            return ResponseEntity.status(410).body(
                    new RedirectErrorResponse(
                            UrlStatus.EXPIRED,
                            "This link expired on " + urlMapping.getExpiresAt(),
                            urlMapping.getUser().getUsername()
                    )
            );
        }

        if (urlMapping.getStatus() == UrlStatus.CLICK_LIMIT_REACHED) {
            return ResponseEntity.status(410).body(
                    new RedirectErrorResponse(
                            UrlStatus.CLICK_LIMIT_REACHED,
                            "This link has reached its maximum click limit of " + urlMapping.getMaxClicks(),
                            urlMapping.getUser().getUsername()
                    )
            );
        }

        if (urlMapping.getStatus() == UrlStatus.DISABLED) {
            return ResponseEntity.status(410).body(
                    new RedirectErrorResponse(
                            UrlStatus.DISABLED,
                            "This link has been disabled by its owner.",
                            urlMapping.getUser().getUsername()
                    )
            );
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Location", urlMapping.getOriginalUrl());
        return ResponseEntity.status(302).headers(httpHeaders).build();
    }
}