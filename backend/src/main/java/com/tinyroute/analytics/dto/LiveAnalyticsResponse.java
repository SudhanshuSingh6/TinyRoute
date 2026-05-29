package com.tinyroute.analytics.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveAnalyticsResponse {

    private Long todayClicks;

    private Long todayUniqueVisitors;

    @Builder.Default
    private Map<String, Long> hourlyClicks = Collections.emptyMap();

    @Builder.Default
    private Map<String, Long> countries = Collections.emptyMap();

    @Builder.Default
    private Map<String, Long> devices = Collections.emptyMap();

    @Builder.Default
    private Map<String, Long> browsers = Collections.emptyMap();

    @Builder.Default
    private Map<String, Long> operatingSystems = Collections.emptyMap();

    @Builder.Default
    private Map<String, Long> referrers = Collections.emptyMap();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdated;
}