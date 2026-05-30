package com.tinyroute.analytics.service;

import com.tinyroute.analytics.dto.ClickEventData;
import com.tinyroute.analytics.dto.LiveAnalyticsResponse;
import com.tinyroute.analytics.infra.RedisAnalyticsConstants;
import com.tinyroute.analytics.infra.RedisAnalyticsEventQueue;
import com.tinyroute.analytics.infra.RedisAnalyticsHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisAnalyticsServiceTest {

    @Mock
    private RedisAnalyticsHelper redisHelper;
    @Mock
    private RedisAnalyticsEventQueue analyticsEventQueue;

    @InjectMocks
    private RedisAnalyticsService redisAnalyticsService;

    private ClickEventData event() {
        return ClickEventData.builder()
                .urlMappingId(1L)
                .ipHash("ip-hash")
                .clickTime(LocalDateTime.of(2026, 5, 29, 10, 0))
                .build();
    }

    @Test
    void recordClick_recordsAtomicallyWithExpectedArgs() throws Exception {
        ClickEventData event = event();
        when(analyticsEventQueue.serialize(event)).thenReturn("json");
        when(analyticsEventQueue.queueKey()).thenReturn("analytics:raw_events");
        when(analyticsEventQueue.queueTtlSeconds()).thenReturn(86_400L);

        redisAnalyticsService.recordClick(event);

        verify(redisHelper).recordClickAtomic(
                anyString(),                 // daily counter key
                anyString(),                 // unique visitor set key
                eq("ip-hash"),
                anyString(),                 // hourly hash key
                eq("10"),                    // hour field
                eq("analytics:raw_events"),
                eq("json"),
                eq(RedisAnalyticsConstants.DAILY_COUNTER_TTL_SECONDS),
                eq(86_400L)
        );
    }

    @Test
    void recordClick_redisError_doesNotThrow() {
        doThrow(new RuntimeException("redis down"))
                .when(redisHelper).recordClickAtomic(
                        any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong());

        assertDoesNotThrow(() -> redisAnalyticsService.recordClick(event()));
    }

    @Test
    void recordLiveAggregates_incrementsAllFiveDimensions() {
        redisAnalyticsService.recordLiveAggregates(
                1L, "IN", "Mobile", "Chrome", "Android", "Direct", LocalDate.of(2026, 5, 29));

        verify(redisHelper).recordLiveAggregatesAtomic(
                anyString(), eq("IN"),
                anyString(), eq("Mobile"),
                anyString(), eq("Chrome"),
                anyString(), eq("Android"),
                anyString(), eq("Direct"),
                eq(RedisAnalyticsConstants.LIVE_HASH_TTL_SECONDS));
    }

    @Test
    void getLiveAnalytics_returnsPopulatedResponse() {
        when(redisHelper.getCounter(anyString())).thenReturn(87L);
        when(redisHelper.getSetSize(anyString())).thenReturn(61L);
        when(redisHelper.getHash(anyString())).thenReturn(Map.of("Chrome", 5L));

        LiveAnalyticsResponse response = redisAnalyticsService.getLiveAnalytics(1L);

        assertEquals(Long.valueOf(87L), response.getTodayClicks());
        assertEquals(Long.valueOf(61L), response.getTodayUniqueVisitors());
    }

    @Test
    void getLiveAnalytics_redisError_returnsEmptyResponse() {
        when(redisHelper.getCounter(anyString())).thenThrow(new RuntimeException("redis down"));

        LiveAnalyticsResponse response = redisAnalyticsService.getLiveAnalytics(1L);

        assertEquals(Long.valueOf(0L), response.getTodayClicks());
        assertEquals(Long.valueOf(0L), response.getTodayUniqueVisitors());
    }
}
