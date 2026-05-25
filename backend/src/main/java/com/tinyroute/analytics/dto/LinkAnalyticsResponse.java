package com.tinyroute.analytics.dto;

import lombok.Data;

import java.util.List;

@Data
public class LinkAnalyticsResponse {
    private long totalClicks;
    private long uniqueClicks;
    private List<ClickByDimensionDTO> clicksByDimension;
    private List<ClickTimeBucketDTO> clicksByTimeBucket;
    private PeakActivityDTO peakActivity;
    private ClickVelocityDTO clickVelocity;
}