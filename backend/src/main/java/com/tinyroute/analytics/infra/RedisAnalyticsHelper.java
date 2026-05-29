package com.tinyroute.analytics.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
                redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception e) {
            log.warn("Failed to increment counter: {}", key, e);
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

    public Long getCounter(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            log.warn("Failed to get counter: {}", key, e);
            return 0L;
        }
    }

    public void addToSet(String key, String member, long ttlSeconds) {
        try {
            redisTemplate.opsForSet().add(key, member);
            if (ttlSeconds > 0) {
                redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception e) {
            log.warn("Failed to add to set: {}", key, e);
        }
    }

    public Long getSetSize(String key) {
        try {
            Long size = redisTemplate.opsForSet().size(key);
            return size != null ? size : 0L;
        } catch (Exception e) {
            log.warn("Failed to get set size: {}", key, e);
            return 0L;
        }
    }

    public void incrementHash(String key, String field, long ttlSeconds) {
        try {
            redisTemplate.opsForHash().increment(key, field, 1L);
            if (ttlSeconds > 0) {
                redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception e) {
            log.warn("Failed to increment hash field: key={} field={}", key, field, e);
        }
    }

    public Map<String, Long> getHash(String key) {
        try {
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, Long> result = new HashMap<>(raw.size());
            for (Map.Entry<Object, Object> entry : raw.entrySet()) {
                try {
                    result.put(
                            String.valueOf(entry.getKey()),
                            Long.parseLong(String.valueOf(entry.getValue()))
                    );
                } catch (NumberFormatException nfe) {
                    log.warn(
                            "Non-numeric hash value skipped: key={} field={} value={}",
                            key, entry.getKey(), entry.getValue()
                    );
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to read hash: {}", key, e);
            return Collections.emptyMap();
        }
    }
}