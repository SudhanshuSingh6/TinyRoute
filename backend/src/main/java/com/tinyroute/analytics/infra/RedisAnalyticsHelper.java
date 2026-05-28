package com.tinyroute.analytics.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class RedisAnalyticsHelper {

    @Qualifier("analyticsRedisTemplate")
    private final StringRedisTemplate redisTemplate;

    public RedisAnalyticsHelper(
            @Qualifier("analyticsRedisTemplate")
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public void incrementCounter(String key, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().increment(key);
            if (ttlSeconds > 0) {
                redisTemplate.expire(
                        key,
                        Duration.ofSeconds(ttlSeconds)
                );
            }
        } catch (Exception e) {
            log.warn("Failed to increment counter: {}", key, e
            );
        }
    }

    /**
     * Add member to Redis Set.
     */
    public void addToSet(
            String key,
            String member,
            long ttlSeconds
    ) {

        try {
            redisTemplate.opsForSet().add(
                    key,
                    member
            );
            if (ttlSeconds > 0) {

                redisTemplate.expire(
                        key,
                        Duration.ofSeconds(ttlSeconds)
                );
            }
        } catch (Exception e) {

            log.warn("Failed to add to set: {}", key, e
            );
        }
    }

    /**
     * Read counter value.
     */
    public Long getCounter(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null
                    ? Long.parseLong(value)
                    : 0L;
        } catch (Exception e) {
            log.warn("Failed to get counter: {}", key, e);
            return 0L;
        }
    }

    /**
     * Redis Set cardinality.
     */
    public Long getSetSize(String key) {
        try {
            Long size = redisTemplate.opsForSet().size(key);
            return size != null
                    ? size
                    : 0L;

        } catch (Exception e) {
            log.warn("Failed to get set size: {}", key, e);
            return 0L;
        }
    }

    public void setCounter(String key, long value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(value));
            if (ttlSeconds > 0) {
                redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception e) {
            log.warn("Failed to set counter: {}", key, e);
        }
    }
}