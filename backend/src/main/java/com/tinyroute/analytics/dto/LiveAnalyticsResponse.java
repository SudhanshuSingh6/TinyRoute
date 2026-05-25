package com.tinyroute.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Response DTO for live analytics endpoint
 *
 * Returned by: GET /api/urls/analytics/live/{shortUrl}
 * Used by: Frontend dashboard polling
 * Updated: Every 3-5 seconds
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveAnalyticsResponse {

    /**
     * Total clicks all-time
     */
    private Long totalClicks;

    /**
     * Clicks today
     */
    private Long todayClicks;

    /**
     * Unique visitors today
     * (deduplicated by IP hash)
     */
    private Long uniqueVisitorsToday;

    /**
     * Timestamp when data was fetched
     * Used by frontend to show "Updated: Just now"
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdated;
}