package com.tinyroute.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClickByDimensionDTO {
    private AnalyticsDimensionType dimension;
    private String key;
    private long count;
}
