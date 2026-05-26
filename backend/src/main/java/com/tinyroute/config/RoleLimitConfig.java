package com.tinyroute.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "role.limits")
public class RoleLimitConfig {

    private int user = 10000;
    private int premium = 100000;
    private int admin = 100000;
}