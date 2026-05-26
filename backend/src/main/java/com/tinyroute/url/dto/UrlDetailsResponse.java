package com.tinyroute.url.dto;

import com.tinyroute.url.entity.UrlStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UrlDetailsResponse {
    private Long id;
    private String shortUrl;
    private String originalUrl;
    private String title;
    private Integer maxClicks;
    private int clickCount;
    private UrlStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}