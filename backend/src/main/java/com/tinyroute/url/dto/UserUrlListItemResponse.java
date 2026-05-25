package com.tinyroute.url.dto;

import com.tinyroute.url.entity.UrlStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserUrlListItemResponse {
    private Long id;
    private String shortUrl;
    private String title;
    private int clickCount;
    private UrlStatus status;
    private LocalDateTime createdDate;
    private LocalDateTime expiresAt;
}