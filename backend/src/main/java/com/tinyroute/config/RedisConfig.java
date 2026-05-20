package com.tinyroute.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username:}")
    private String redisUsername;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean redisSsl;

    @Bean
    public RedisClient redisClient() {

        RedisURI.Builder builder = RedisURI.Builder
                .redis(redisHost, redisPort)
                .withSsl(redisSsl);

        if (!redisPassword.isBlank()) {

            if (!redisUsername.isBlank()) {
                builder.withAuthentication(
                        redisUsername,
                        redisPassword.toCharArray()
                );
            } else {
                builder.withPassword(
                        redisPassword.toCharArray()
                );
            }
        }

        return RedisClient.create(builder.build());
    }

    @Bean
    public StatefulRedisConnection<String, byte[]> redisConnection(
            RedisClient redisClient
    ) {

        RedisCodec<String, byte[]> codec =
                RedisCodec.of(
                        StringCodec.UTF8,
                        ByteArrayCodec.INSTANCE
                );

        return redisClient.connect(codec);
    }
}