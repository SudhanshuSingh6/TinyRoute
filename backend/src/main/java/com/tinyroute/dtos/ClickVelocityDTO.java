package com.tinyroute.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClickVelocityDTO {
    private long last24h;       // last 24 hours
    private long previous24h;
    private String trend;       // "UP", "DOWN", or "STABLE"
}