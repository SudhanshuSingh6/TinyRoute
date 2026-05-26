package com.tinyroute.exception.response;

import com.tinyroute.url.entity.UrlStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RedirectErrorResponse {
    private UrlStatus status;    // EXPIRED / CLICK_LIMIT_REACHED / DISABLED
    private String message;      // reason
}