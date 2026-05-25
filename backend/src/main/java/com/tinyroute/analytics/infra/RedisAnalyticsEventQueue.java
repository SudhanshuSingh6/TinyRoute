package com.tinyroute.analytics.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinyroute.analytics.dto.ClickEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisAnalyticsEventQueue {

    private static final String RAW_EVENTS_QUEUE_KEY = "analytics:raw_events";

    /**
     * Safety TTL in case queue becomes abandoned
     */
    private static final Duration QUEUE_TTL = Duration.ofDays(1);

    @Qualifier("analyticsRedisTemplate")
    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * Push raw analytics event into Redis List
     *
     * Hot path operation:
     * ultra-fast
     * no enrichment
     * no DB interaction
     */
    public void enqueue(ClickEventData event) {

        try {

            String json = objectMapper.writeValueAsString(event);

            redisTemplate.opsForList().leftPush(
                    RAW_EVENTS_QUEUE_KEY,
                    json
            );

            redisTemplate.expire(
                    RAW_EVENTS_QUEUE_KEY,
                    QUEUE_TTL
            );

        } catch (JsonProcessingException e) {

            log.warn(
                    "Failed to enqueue analytics event for urlId={}",
                    event.getUrlMappingId(),
                    e
            );
        }
    }

    /**
     * Drain batch from Redis List
     *
     * Uses RPOP so events are processed FIFO-ish
     */
    public List<ClickEventData> drainBatch(int maxBatchSize) {
        try {
            List<String> rawEvents = redisTemplate.opsForList()
                    .rightPop(RAW_EVENTS_QUEUE_KEY, maxBatchSize); // single command

            if (rawEvents == null || rawEvents.isEmpty()) return List.of();

            List<ClickEventData> batch = new ArrayList<>(rawEvents.size());
            for (String json : rawEvents) {
                try {
                    batch.add(objectMapper.readValue(json, ClickEventData.class));
                } catch (Exception e) {
                    log.warn("Failed to deserialize analytics event: {}", json, e);
                }
            }
            return batch;
        } catch (Exception e) {
            log.warn("Failed draining analytics Redis queue", e);
            return List.of();
        }
    }

    /**
     * Queue size monitoring
     */
    public long size() {

        try {

            Long size = redisTemplate.opsForList().size(
                    RAW_EVENTS_QUEUE_KEY
            );

            return size != null ? size : 0L;

        } catch (Exception e) {

            log.warn(
                    "Failed to get analytics queue size",
                    e
            );

            return 0L;
        }
    }

    /**
     * Whether queue currently has events
     */
    public boolean hasEvents() {
        return size() > 0;
    }
}