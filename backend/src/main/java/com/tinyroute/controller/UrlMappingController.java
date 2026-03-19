package com.tinyroute.controller;

import com.tinyroute.dtos.ClickEventDTO;
import com.tinyroute.dtos.UrlMappingDTO;
import com.tinyroute.models.UrlMapping;
import com.tinyroute.models.UrlStatus;
import com.tinyroute.models.User;
import com.tinyroute.repository.UrlMappingRepository;
import com.tinyroute.service.UrlMappingService;
import com.tinyroute.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/urls")
@AllArgsConstructor
public class UrlMappingController {

    private UrlMappingService urlMappingService;
    private UserService userService;
    private UrlMappingRepository urlMappingRepository;

    @PostMapping("/shorten")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createShortUrl(@RequestBody Map<String, String> request,
                                            Principal principal) {
        String originalUrl = request.get("originalUrl");
        String customAlias = request.get("customAlias");
        String expiresAtStr = request.get("expiresAt");
        String maxClicksStr = request.get("maxClicks");
        String title = request.get("title");                          // NEW
        boolean isPublic = !"false".equals(request.get("isPublic")); // NEW — default true

        LocalDateTime expiresAt = (expiresAtStr != null && !expiresAtStr.isBlank())
                ? LocalDateTime.parse(expiresAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;

        Integer maxClicks = (maxClicksStr != null && !maxClicksStr.isBlank())
                ? Integer.parseInt(maxClicksStr)
                : null;

        User user = userService.findByUsername(principal.getName());

        try {
            UrlMappingDTO urlMappingDTO = urlMappingService.createShortUrl(
                    originalUrl, customAlias, expiresAt, maxClicks, title, isPublic, user);
            return ResponseEntity.ok(urlMappingDTO);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // NEW — toggle a URL between ACTIVE and DISABLED
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> toggleUrl(@PathVariable Long id, Principal principal) {
        UrlMapping urlMapping = urlMappingRepository.findById(id).orElse(null);

        if (urlMapping == null) {
            return ResponseEntity.notFound().build();
        }

        // make sure it belongs to this user
        if (!urlMapping.getUser().getUsername().equals(principal.getName())) {
            return ResponseEntity.status(403).body("You don't own this URL.");
        }

        // toggle between ACTIVE and DISABLED
        if (urlMapping.getStatus() == UrlStatus.ACTIVE) {
            urlMapping.setStatus(UrlStatus.DISABLED);
        } else if (urlMapping.getStatus() == UrlStatus.DISABLED) {
            urlMapping.setStatus(UrlStatus.ACTIVE);
        } else {
            return ResponseEntity.badRequest()
                    .body("Cannot toggle a URL with status: " + urlMapping.getStatus());
        }

        urlMappingRepository.save(urlMapping);
        return ResponseEntity.ok("Status updated to: " + urlMapping.getStatus());
    }

    @PostMapping("/myurls")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<UrlMappingDTO>> getUserUrls(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        List<UrlMappingDTO> urls = urlMappingService.getUrlsByUser(user);
        return ResponseEntity.ok(urls);
    }

    @PostMapping("/analytics/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ClickEventDTO>> getUserAnalytics(
            @PathVariable String shortUrl,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime start = LocalDateTime.parse(startDate, formatter);
        LocalDateTime end = LocalDateTime.parse(endDate, formatter);
        List<ClickEventDTO> clickEventDTOS = urlMappingService.getClickEventsByDate(shortUrl, start, end);
        return ResponseEntity.ok(clickEventDTOS);
    }

    @GetMapping("/totalClicks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<LocalDate, Long>> getTotalClicksByDate(
            Principal principal,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        User user = userService.findByUsername(principal.getName());
        LocalDate start = LocalDate.parse(startDate, formatter);
        LocalDate end = LocalDate.parse(endDate, formatter);
        Map<LocalDate, Long> totalClicks = urlMappingService.getTotalClicksByUserAndDate(user, start, end);
        return ResponseEntity.ok(totalClicks);
    }
}