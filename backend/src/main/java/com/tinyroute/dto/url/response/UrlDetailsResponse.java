package com.tinyroute.dto.url.response;

import com.tinyroute.entity.UrlStatus;
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
    private LocalDateTime createdDate;
    private LocalDateTime expiresAt;
    private LocalDateTime lastClickedAt;
}