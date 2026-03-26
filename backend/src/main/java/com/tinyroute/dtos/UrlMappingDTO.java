package com.tinyroute.dtos;

import com.tinyroute.entity.UrlStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UrlMappingDTO {
    private Long id;
    private String originalUrl;
    private String shortUrl;
    private String customAlias;
    private String title;               // new
    private int clickCount;
    private LocalDateTime createdDate;
    private LocalDateTime expiresAt;
    private LocalDateTime lastClickedAt; // new
    private Integer maxClicks;
    private boolean isPublic;           // new
    private UrlStatus status;           // new
    private String username;
}