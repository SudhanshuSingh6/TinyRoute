package com.tinyroute.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClickTimeBucketDTO {
    private TimeBucketType type;
    private String bucket;
    private long count;
}