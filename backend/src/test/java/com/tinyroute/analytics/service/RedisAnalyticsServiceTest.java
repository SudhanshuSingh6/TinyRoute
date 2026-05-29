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
    void recordClick_incrementsCountersAndEnqueues() {
        ClickEventData event = event();

        redisAnalyticsService.recordClick(event);

        verify(redisHelper).incrementCounter(anyString(), eq(RedisAnalyticsConstants.DAILY_COUNTER_TTL_SECONDS));
        verify(redisHelper).addToSet(anyString(), eq("ip-hash"), eq(RedisAnalyticsConstants.UNIQUE_SET_TTL_SECONDS));
        verify(redisHelper).incrementHash(anyString(), eq("10"), eq(RedisAnalyticsConstants.LIVE_HASH_TTL_SECONDS));
        verify(analyticsEventQueue).enqueue(event);
    }

    @Test
    void recordClick_redisError_doesNotThrow() {
        doThrow(new RuntimeException("redis down"))
                .when(redisHelper).incrementCounter(anyString(), anyLong());

        assertDoesNotThrow(() -> redisAnalyticsService.recordClick(event()));
    }

    @Test
    void recordLiveAggregates_incrementsAllFiveDimensions() {
        redisAnalyticsService.recordLiveAggregates(
                1L, "IN", "Mobile", "Chrome", "Android", "Direct", LocalDate.of(2026, 5, 29));

        verify(redisHelper, times(5))
                .incrementHash(anyString(), anyString(), eq(RedisAnalyticsConstants.LIVE_HASH_TTL_SECONDS));
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
