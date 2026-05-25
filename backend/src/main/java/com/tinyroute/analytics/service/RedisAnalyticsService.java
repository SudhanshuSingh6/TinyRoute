package com.tinyroute.analytics.service;

import com.tinyroute.analytics.dto.ClickEventData;
import com.tinyroute.analytics.dto.LiveAnalyticsResponse;
import com.tinyroute.analytics.infra.RedisAnalyticsConstants;
import com.tinyroute.analytics.infra.RedisAnalyticsEventQueue;
import com.tinyroute.analytics.infra.RedisAnalyticsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisAnalyticsService {

    private static final List<String> DEVICE_BUCKETS = List.of(
            "mobile",
            "desktop",
            "tablet",
            "bot",
            "unknown"
    );

    private final RedisAnalyticsHelper redisHelper;
    private final RedisAnalyticsEventQueue analyticsEventQueue;

    /**
     * FINAL HOT PATH ANALYTICS FLOW
     *
     * ONLY:
     * - lightweight Redis counters
     * - realtime unique visitors
     * - enqueue raw event
     *
     * NO:
     * - DB writes
     * - UA parsing
     * - geo lookup
     * - heavy enrichment
     */
    public void recordClick(ClickEventData event) {

        try {

            LocalDate today = clickDate(event);
            Long urlMappingId = event.getUrlMappingId();

            // Global clicks
            redisHelper.incrementCounter(
                    RedisAnalyticsConstants.globalClicksKey(),
                    0
            );

            // Total clicks per URL
            redisHelper.incrementCounter(
                    RedisAnalyticsConstants.urlTotalClicksKey(urlMappingId),
                    0
            );

            // Daily clicks
            redisHelper.incrementCounter(
                    RedisAnalyticsConstants.urlDailyClicksKey(
                            urlMappingId,
                            today
                    ),
                    RedisAnalyticsConstants.DAILY_COUNTER_TTL_SECONDS
            );

            // Unique visitors today
            redisHelper.addToSet(
                    RedisAnalyticsConstants.urlUniqueVisitorsKey(
                            urlMappingId,
                            today
                    ),
                    event.getIpHash(),
                    RedisAnalyticsConstants.UNIQUE_SET_TTL_SECONDS
            );

            // Async queue
            analyticsEventQueue.enqueue(event);

        } catch (Exception e) {

            log.warn(
                    "Failed to record realtime analytics for urlId={}",
                    event.getUrlMappingId(),
                    e
            );
        }
    }

    /**
     * Called ONLY by background worker
     * after enrichment.
     */
    public void recordEnrichedAggregates(
            Long urlMappingId,
            String browser,
            String country,
            String deviceType,
            LocalDate date
    ) {

        try {

            LocalDate metricDate =
                    date != null ? date : LocalDate.now();

            redisHelper.incrementCounter(
                    RedisAnalyticsConstants.urlBrowserKey(
                            urlMappingId,
                            normalizeDimension(browser),
                            metricDate
                    ),
                    RedisAnalyticsConstants.DAILY_COUNTER_TTL_SECONDS
            );

            redisHelper.incrementCounter(
                    RedisAnalyticsConstants.urlCountryKey(
                            urlMappingId,
                            normalizeDimension(country),
                            metricDate
                    ),
                    RedisAnalyticsConstants.DAILY_COUNTER_TTL_SECONDS
            );

            redisHelper.incrementCounter(
                    RedisAnalyticsConstants.urlDeviceKey(
                            urlMappingId,
                            normalizeDimension(deviceType),
                            metricDate
                    ),
                    RedisAnalyticsConstants.DAILY_COUNTER_TTL_SECONDS
            );

        } catch (Exception e) {

            log.warn(
                    "Failed to record enriched analytics for urlId={}",
                    urlMappingId,
                    e
            );
        }
    }

    /**
     * Live realtime analytics.
     *
     * Redis ONLY.
     */
    public LiveAnalyticsResponse getLiveAnalytics(Long urlMappingId) {

        try {

            LocalDate today = LocalDate.now();

            Long totalClicks = redisHelper.getCounter(
                    RedisAnalyticsConstants.urlTotalClicksKey(
                            urlMappingId
                    )
            );

            Long todayClicks = redisHelper.getCounter(
                    RedisAnalyticsConstants.urlDailyClicksKey(
                            urlMappingId,
                            today
                    )
            );

            Long uniqueVisitors = redisHelper.getSetSize(
                    RedisAnalyticsConstants.urlUniqueVisitorsKey(
                            urlMappingId,
                            today
                    )
            );

            return LiveAnalyticsResponse.builder()
                    .totalClicks(totalClicks)
                    .todayClicks(todayClicks)
                    .uniqueVisitorsToday(uniqueVisitors)
                    .lastUpdated(LocalDateTime.now())
                    .build();

        } catch (Exception e) {

            log.warn(
                    "Failed to get live analytics for urlId={}",
                    urlMappingId,
                    e
            );

            return LiveAnalyticsResponse.builder()
                    .totalClicks(0L)
                    .todayClicks(0L)
                    .uniqueVisitorsToday(0L)
                    .lastUpdated(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Device breakdown for live dashboard.
     */
    public Map<String, Long> getDeviceBreakdownToday(Long urlMappingId) {

        Map<String, Long> breakdown = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        for (String device : DEVICE_BUCKETS) {

            Long count = redisHelper.getCounter(
                    RedisAnalyticsConstants.urlDeviceKey(
                            urlMappingId,
                            device,
                            today
                    )
            );

            if (count > 0) {
                breakdown.put(device, count);
            }
        }

        return breakdown;
    }

    /**
     * Queue monitoring endpoint.
     */
    public Long getRawQueueSize() {
        return analyticsEventQueue.size();
    }

    public Long getGlobalClicks() {

        try {

            return redisHelper.getCounter(
                    RedisAnalyticsConstants.globalClicksKey()
            );

        } catch (Exception e) {

            log.warn(
                    "Failed to get global clicks",
                    e
            );

            return 0L;
        }
    }

    public Long getUrlTotalClicks(Long urlMappingId) {

        return redisHelper.getCounter(
                RedisAnalyticsConstants.urlTotalClicksKey(
                        urlMappingId
                )
        );
    }

    private LocalDate clickDate(ClickEventData event) {

        return event.getClickTime() != null
                ? event.getClickTime().toLocalDate()
                : LocalDate.now();
    }

    private String normalizeDimension(String value) {

        if (!StringUtils.hasText(value)) {
            return "unknown";
        }

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return normalized.isBlank()
                ? "unknown"
                : normalized;
    }
}