package com.tinyroute.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight DTO for queuing click events
 * Contains raw metadata only (not enriched)
 *
 * Flow:
 * 1. Created in redirect thread (fast)
 * 2. Queued for async processing
 * 3. Background worker enriches this data
 * 4. Saved to database as ClickEvent entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEventData {

    // Required: URL mapping ID
    private Long urlMappingId;

    // Raw request metadata (from HTTP request)
    private String ip;
    private String ipHash;
    private String userAgent;
    private String referer;
    private String language;
    private LocalDateTime clickTime;

    // Device parsing (will be enriched in background)
    private String deviceType;  // MOBILE, DESKTOP, TABLET
    private String browser;     // CHROME, FIREFOX, SAFARI, etc
    private String os;          // WINDOWS, ANDROID, IOS, etc

    // Geo lookup (will be enriched in background)
    private String country;
    private String city;

    /**
     * Flag: whether enrichment is complete
     * Used by background worker
     */
    private boolean enriched = false;
}