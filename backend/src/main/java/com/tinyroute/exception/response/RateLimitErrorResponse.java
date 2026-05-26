package com.tinyroute.exception.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RateLimitErrorResponse {
    private String error;
    private String endpoint;
    private long limit;
    private long remaining;
    private long retryAfter;
    private String message;
}