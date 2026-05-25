package com.tinyroute.url.dto;

import com.tinyroute.url.entity.UrlStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UrlStatusResponse {
    private Long id;
    private UrlStatus status;
    private String message;
}
