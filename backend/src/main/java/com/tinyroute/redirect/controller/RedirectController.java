package com.tinyroute.redirect.controller;

import com.tinyroute.exception.response.RedirectErrorResponse;
import com.tinyroute.ratelimit.RateLimitEndpoint;
import com.tinyroute.ratelimit.RateLimitHelper;
import com.tinyroute.redirect.service.RedirectService;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.entity.UrlStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Tag(name = "Redirect", description = "Public short URL redirect — no auth required")
@Slf4j
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private static final DateTimeFormatter READABLE_DATE =
            DateTimeFormatter.ofPattern(
                    "MMMM d, yyyy",
                    Locale.ENGLISH
            );

    private final RedirectService redirectService;
    private final RateLimitHelper rateLimitHelper;

    @Operation(
            summary = "Redirect to original URL",
            description = "Resolves a short URL and redirects. "
                    + "Queues raw analytics for asynchronous enrichment. "
                    + "Rate limited to 60 requests per minute per IP."
    )
    @ApiResponse(responseCode = "302", description = "Redirect to original URL")
    @ApiResponse(responseCode = "404", description = "Short URL not found")
    @ApiResponse(responseCode = "410", description = "URL expired, click limit reached, or disabled")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded — too many requests from this IP")
    @ApiResponse(responseCode = "500", description = "Unexpected redirect failure")
    @GetMapping("/{shortUrl}")
    public ResponseEntity<?> redirect(
            @Parameter(description = "Short URL code", example = "abc12345")
            @PathVariable String shortUrl,
            HttpServletRequest request
    ) {

       // ResponseEntity<?> limitResponse =
             //   rateLimitHelper.applyPublicRateLimit(
               //         request,
               //         RateLimitEndpoint.REDIRECT,
            //            "/" + shortUrl
              //  );

      //  if (limitResponse != null) {
       //     return limitResponse;
      //  }

        UrlMapping urlMapping =
                redirectService.getOriginalUrl(
                        shortUrl,
                        request
                );

        if (urlMapping == null) {
            return ResponseEntity.notFound().build();
        }

        if (urlMapping.getStatus() == UrlStatus.DISABLED) {

            return gone(
                    urlMapping,
                    "This link has been disabled by its owner."
            );
        }

        if (urlMapping.getStatus() == UrlStatus.EXPIRED) {

            return gone(
                    urlMapping,
                    "This link expired on "
                            + urlMapping.getExpiresAt().format(READABLE_DATE)
            );
        }

        if (urlMapping.getStatus() == UrlStatus.CLICK_LIMIT_REACHED) {

            return gone(
                    urlMapping,
                    "This link has reached its maximum click limit of "
                            + urlMapping.getMaxClicks()
            );
        }

        URI destination =
                toSafeRedirectUri(
                        urlMapping.getOriginalUrl()
                );

        if (destination == null) {

            return gone(
                    urlMapping,
                    "This link has an invalid destination URL."
            );
        }

        HttpHeaders headers = new HttpHeaders();

        headers.setLocation(destination);

        return ResponseEntity
                .status(302)
                .headers(headers)
                .build();
    }

    private ResponseEntity<RedirectErrorResponse> gone(
            UrlMapping urlMapping,
            String message
    ) {

        return ResponseEntity
                .status(410)
                .body(
                        new RedirectErrorResponse(
                                urlMapping.getStatus(),
                                message
                        )
                );
    }

    private URI toSafeRedirectUri(String rawUrl) {

        try {

            URI uri = URI.create(rawUrl);

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null || host.isBlank()) {
                return null;
            }

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);

            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return null;
            }

            return uri;

        } catch (Exception ex) {
            return null;
        }
    }
}