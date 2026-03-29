package com.tinyroute.dto.url.response;

import lombok.Data;

@Data
public class ShortUrlResponse {
    private Long id;
    private String shortUrl;
    private String customAlias;
    private String originalUrl;
}
