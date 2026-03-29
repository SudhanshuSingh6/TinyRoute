package com.tinyroute.dto.user.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PublicUrlDTO {
    private String shortUrl;
    private String title;
    private String originalUrl;
    private LocalDateTime createdDate;
}
