package com.tinyroute.dto.error;

import com.tinyroute.entity.UrlStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RedirectErrorResponse {
    private UrlStatus status;    // EXPIRED / CLICK_LIMIT_REACHED / DISABLED
    private String message;      // reason
}