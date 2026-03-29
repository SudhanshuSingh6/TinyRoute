package com.tinyroute.dto.url.response;

import com.tinyroute.entity.UrlStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UrlStatusResponse {
    private Long id;
    private UrlStatus status;
    private String message;
}
