package com.tinyroute.ratelimit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum RateLimitPlan {

    AUTH(10, Duration.ofMinutes(1)),
    ANALYTICS(20, Duration.ofMinutes(1)),
    MY_URLS(30, Duration.ofMinutes(1)),
    URL_MANAGEMENT(20, Duration.ofMinutes(1)),
    SHORTEN(50, Duration.ofMinutes(1)),
    REDIRECT(60, Duration.ofMinutes(1));

    private final int capacity;
    private final Duration duration;
}