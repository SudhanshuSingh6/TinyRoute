package com.tinyroute.analytics.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RedisAnalyticsHelper {

    // Records one click in a single round-trip: increments the daily counter,
    // adds the visitor to today's unique set, bumps the hourly hash, and pushes
    // the raw event onto the processing queue. TTLs are set only when a key has
    // none yet (TTL < 0), so we don't re-EXPIRE on every click.
    // KEYS[1]=daily KEYS[2]=unique KEYS[3]=hourly KEYS[4]=queue
    // ARGV[1]=ipHash ARGV[2]=hourField ARGV[3]=eventJson
    // ARGV[4]=analyticsTtlSeconds ARGV[5]=queueTtlSeconds
    private static final RedisScript<Long> RECORD_CLICK_SCRIPT = RedisScript.of(
            """
            redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[4]) end
            redis.call('SADD', KEYS[2], ARGV[1])
            if redis.call('TTL', KEYS[2]) < 0 then redis.call('EXPIRE', KEYS[2], ARGV[4]) end
            redis.call('HINCRBY', KEYS[3], ARGV[2], 1)
            if redis.call('TTL', KEYS[3]) < 0 then redis.call('EXPIRE', KEYS[3], ARGV[4]) end
            redis.call('LPUSH', KEYS[4], ARGV[3])
            if redis.call('TTL', KEYS[4]) < 0 then redis.call('EXPIRE', KEYS[4], ARGV[5]) end
            return 1
            """,
            Long.class
    );

    // Increments all five live-aggregate dimension hashes in a single round-trip.
    // Each KEYS[i] is a dimension hash and ARGV[i] its field to bump; the TTL is
    // (re)applied only when the key has none yet (TTL < 0).
    // KEYS[1..5]=country/device/browser/os/referrer hashes
    // ARGV[1..5]=field values  ARGV[6]=ttlSeconds
    private static final RedisScript<Long> RECORD_LIVE_AGGREGATES_SCRIPT = RedisScript.of(
            """
            for i = 1, 5 do
                redis.call('HINCRBY', KEYS[i], ARGV[i], 1)
                if redis.call('TTL', KEYS[i]) < 0 then redis.call('EXPIRE', KEYS[i], ARGV[6]) end
            end
            return 1
            """,
            Long.class
    );

    @Qualifier("analyticsRedisTemplate")
    private final StringRedisTemplate redisTemplate;

    public RedisAnalyticsHelper(
            @Qualifier("analyticsRedisTemplate")
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public void recordClickAtomic(
            String dailyKey,
            String uniqueKey,
            String ipHash,
            String hourlyKey,
            String hourField,
            String queueKey,
            String eventJson,
            long analyticsTtlSeconds,
            long queueTtlSeconds
    ) {
        try {
            redisTemplate.execute(
                    RECORD_CLICK_SCRIPT,
                    List.of(dailyKey, uniqueKey, hourlyKey, queueKey),
                    ipHash,
                    hourField,
                    eventJson,
                    String.valueOf(analyticsTtlSeconds),
                    String.valueOf(queueTtlSeconds)
            );
        } catch (Exception e) {
            log.warn("Failed to record click for dailyKey={}", dailyKey, e);
        }
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

    public void recordLiveAggregatesAtomic(
            String countryKey, String countryField,
            String deviceKey, String deviceField,
            String browserKey, String browserField,
            String osKey, String osField,
            String referrerKey, String referrerField,
            long ttlSeconds
    ) {
        try {
            redisTemplate.execute(
                    RECORD_LIVE_AGGREGATES_SCRIPT,
                    List.of(countryKey, deviceKey, browserKey, osKey, referrerKey),
                    countryField,
                    deviceField,
                    browserField,
                    osField,
                    referrerField,
                    String.valueOf(ttlSeconds)
            );
        } catch (Exception e) {
            log.warn("Failed to record live aggregates for countryKey={}", countryKey, e);
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