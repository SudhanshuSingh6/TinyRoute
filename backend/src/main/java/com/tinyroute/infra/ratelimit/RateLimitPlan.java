package com.tinyroute.infra.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

@Getter
@AllArgsConstructor
public enum RateLimitPlan {

    AUTH(10, 10, Duration.ofMinutes(1)),
    ANALYTICS(20, 20, Duration.ofMinutes(1)),
    MY_URLS(30, 30, Duration.ofMinutes(1)),
    URL_MANAGEMENT(20, 20, Duration.ofMinutes(1)),
    SHORTEN(50, 50, Duration.ofMinutes(1));

    private final int capacity;
    private final int refillTokens;
    private final Duration duration;
}