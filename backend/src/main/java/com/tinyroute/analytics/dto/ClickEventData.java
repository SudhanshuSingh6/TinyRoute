package com.tinyroute.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private String deviceType;
    private String browser;
    private String os;

    private String country;
    private String city;

    private boolean enriched = false;
}