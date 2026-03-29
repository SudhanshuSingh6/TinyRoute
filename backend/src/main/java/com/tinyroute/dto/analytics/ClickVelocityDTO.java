package com.tinyroute.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClickVelocityDTO {
    private long secondHalf;
    private long firstHalf;
    private String trend;       // "UP", "DOWN", or "STABLE"
}