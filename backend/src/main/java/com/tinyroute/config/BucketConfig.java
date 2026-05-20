package com.tinyroute.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@RequiredArgsConstructor
public class BucketConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedisClient redisClient() {

        @Bean
        public RedisClient redisClient () {

            RedisURI.Builder builder =
                    RedisURI.Builder.redis(redisHost, redisPort)
                            .withSsl(true);

            if (!redisPassword.isBlank()) {
                builder.withPassword(redisPassword.toCharArray());
            }

            return RedisClient.create(builder.build());
        }

        if (!redisPassword.isBlank()) {
            builder.withPassword(redisPassword.toCharArray());
        }

        return RedisClient.create(builder.build());
    }

    @Bean
    public StatefulRedisConnection<String, byte[]> redisConnection(
            RedisClient redisClient
    ) {

        RedisCodec<String, byte[]> codec =
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);

        return redisClient.connect(codec);
    }

    @Bean
    public ProxyManager<String> proxyManager(
            StatefulRedisConnection<String, byte[]> connection
    ) {
        return LettuceBasedProxyManager.builderFor(connection).build();
    }
}