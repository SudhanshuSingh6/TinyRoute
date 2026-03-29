package com.tinyroute.dto.analytics.response;

import com.tinyroute.dto.analytics.*;
import lombok.Data;

import java.util.List;

@Data
public class LinkAnalyticsResponse {
    private long totalClicks;
    private long uniqueClicks;
    private List<ClickByDimensionDTO> clicksByDimension;
    private List<ClickTimeBucketDTO> clicksByTimeBucket;
    private List<DailyClickCountDTO> dailyClicks;
    private PeakActivityDTO peakActivity;
    private ClickVelocityDTO clickVelocity;
}