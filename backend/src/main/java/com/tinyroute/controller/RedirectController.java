package com.tinyroute.controller;

import com.tinyroute.dtos.RedirectErrorResponse;
import com.tinyroute.models.UrlMapping;
import com.tinyroute.models.UrlStatus;
import com.tinyroute.service.UrlMappingService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class RedirectController {

    private UrlMappingService urlMappingService;

    @GetMapping("/{shortUrl}")
    public ResponseEntity<?> redirect(@PathVariable String shortUrl) {
        UrlMapping urlMapping = urlMappingService.getOriginalUrl(shortUrl);

        // 404 — short URL not in DB
        if (urlMapping == null) {
            return ResponseEntity.notFound().build();
        }

        // 410 — expired
        if (urlMapping.getStatus() == UrlStatus.EXPIRED) {
            return ResponseEntity.status(410).body(
                    new RedirectErrorResponse(
                            UrlStatus.EXPIRED,
                            "This link expired on " + urlMapping.getExpiresAt(),
                            urlMapping.getUser().getUsername()
                    )
            );
        }

        // 410 — click limit reached
        if (urlMapping.getStatus() == UrlStatus.CLICK_LIMIT_REACHED) {
            return ResponseEntity.status(410).body(
                    new RedirectErrorResponse(
                            UrlStatus.CLICK_LIMIT_REACHED,
                            "This link has reached its maximum click limit of " + urlMapping.getMaxClicks(),
                            urlMapping.getUser().getUsername()
                    )
            );
        }

        // 410 — manually disabled
        if (urlMapping.getStatus() == UrlStatus.DISABLED) {
            return ResponseEntity.status(410).body(
                    new RedirectErrorResponse(
                            UrlStatus.DISABLED,
                            "This link has been disabled by its owner.",
                            urlMapping.getUser().getUsername()
                    )
            );
        }

        // 302 — valid redirect
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Location", urlMapping.getOriginalUrl());
        return ResponseEntity.status(302).headers(httpHeaders).build();
    }
}