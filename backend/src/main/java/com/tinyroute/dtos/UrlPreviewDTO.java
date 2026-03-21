package com.tinyroute.dtos;

import lombok.Data;

@Data
public class UrlPreviewDTO {
    private String title;
    private String description;
    private String imageUrl;
    private String originalUrl;
}