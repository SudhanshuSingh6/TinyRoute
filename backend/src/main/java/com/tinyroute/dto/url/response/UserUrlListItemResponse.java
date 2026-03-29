package com.tinyroute.dto.url.response;

import com.tinyroute.entity.UrlStatus;
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