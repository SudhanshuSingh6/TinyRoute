package com.tinyroute.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BucketConfig {

    @Bean
    public ProxyManager<String> proxyManager(
            StatefulRedisConnection<String, byte[]> connection
    ) {

        return LettuceBasedProxyManager
                .builderFor(connection)
                .build();
    }
}