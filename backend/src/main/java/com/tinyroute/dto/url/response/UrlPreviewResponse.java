package com.tinyroute.dto.url.response;

import lombok.Data;

@Data
public class UrlPreviewResponse {
    private String title;
    private String description;
    private String imageUrl;
    private String originalUrl;
}