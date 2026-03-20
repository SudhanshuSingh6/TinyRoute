package com.tinyroute.dtos;

import lombok.Data;

import java.util.Map;

@Data
public class AnalyticsDTO {
    private long totalClicks;
    private long uniqueClicks;
    private Map<String, Long> clicksByCountry;
    private Map<String, Long> clicksByDevice;
    private Map<String, Long> clicksByBrowser;
    private Map<String, Long> clicksByOs;
    private Map<String, Long> clicksByReferrer;
    private Map<Integer, Long> clicksByHour;        // 0-23
    private Map<String, Long> clicksByDayOfWeek;    // Monday, Tuesday...
    private int peakHour;                           // hour with most clicks (0-23)
    private ClickVelocityDTO clickVelocity;         // trend vs yesterday
}