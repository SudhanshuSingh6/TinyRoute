package com.tinyroute.infra.ratelimit;

import lombok.Getter;

@Getter
public enum RateLimitEndpoint {
    AUTH(RateLimitPlan.AUTH,
            "Too many authentication requests. Please try again later."),
    SHORTEN(RateLimitPlan.SHORTEN,
            "Too many shorten requests. Please try again later."),
    ANALYTICS(RateLimitPlan.ANALYTICS,
            "Too many analytics requests. Please try again later."),
    MY_URLS(RateLimitPlan.MY_URLS,
            "Too many URL list requests. Please try again later."),
    URL_MANAGEMENT(RateLimitPlan.URL_MANAGEMENT,
            "Too many URL management requests. Please try again later.");

    private final RateLimitPlan plan;
    private final String exceededMessage;

    RateLimitEndpoint(RateLimitPlan plan, String exceededMessage) {
        this.plan = plan;
        this.exceededMessage = exceededMessage;
    }
}
