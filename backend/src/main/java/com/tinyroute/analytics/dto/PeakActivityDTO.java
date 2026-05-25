package com.tinyroute.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeakActivityDTO {
    private TimeBucketType type;
    private String label;
    private long count;
}