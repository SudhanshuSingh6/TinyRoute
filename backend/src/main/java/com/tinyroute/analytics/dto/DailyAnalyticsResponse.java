package com.tinyroute.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response for daily breakdown endpoint
 *
 * Returned by: GET /api/urls/analytics/daily/{shortUrl}
 * Used by: Frontend graphs/charts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAnalyticsResponse {

    /**
     * Daily click counts (last 7 days)
     * Key: "2026-05-24", Value: 45
     */
    private Map<String, Long> dailyClicks;

    /**
     * Device breakdown for today
     * Key: "mobile", Value: 30
     */
    private Map<String, Long> deviceBreakdown;

    /**
     * Total clicks (for reference)
     */
    private Long totalClicks;
}