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
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisAnalyticsService {

    private final RedisAnalyticsHelper redisHelper;
    private final RedisAnalyticsEventQueue analyticsEventQueue;

    public void recordClick(ClickEventData event) {

        try {

            LocalDate today = clickDate(event);
            Long urlMappingId = event.getUrlMappingId();
            String hour = String.format("%02d", clickHour(event));

            redisHelper.recordClickAtomic(
                    RedisAnalyticsConstants.urlDailyClicksKey(urlMappingId, today),
                    RedisAnalyticsConstants.urlUniqueVisitorsKey(urlMappingId, today),
                    event.getIpHash(),
                    RedisAnalyticsConstants.urlHourlyClicksKey(urlMappingId, today),
                    hour,
                    analyticsEventQueue.queueKey(),
                    analyticsEventQueue.serialize(event),
                    RedisAnalyticsConstants.DAILY_COUNTER_TTL_SECONDS,
                    analyticsEventQueue.queueTtlSeconds()
            );

        } catch (Exception e) {
            log.warn("Failed to record realtime analytics for urlId={}", event.getUrlMappingId(), e);
        }
    }

    public void recordLiveAggregates(
            Long urlMappingId,
            String country,
            String deviceType,
            String browser,
            String os,
            String referrer,
            LocalDate date
    ) {
        try {
            LocalDate metricDate = date != null ? date : LocalDate.now();
            long ttl = RedisAnalyticsConstants.LIVE_HASH_TTL_SECONDS;

            redisHelper.recordLiveAggregatesAtomic(
                    RedisAnalyticsConstants.urlCountryHashKey(urlMappingId, metricDate),
                    normalizeDimension(country),
                    RedisAnalyticsConstants.urlDeviceHashKey(urlMappingId, metricDate),
                    normalizeDimension(deviceType),
                    RedisAnalyticsConstants.urlBrowserHashKey(urlMappingId, metricDate),
                    normalizeDimension(browser),
                    RedisAnalyticsConstants.urlOsHashKey(urlMappingId, metricDate),
                    normalizeDimension(os),
                    RedisAnalyticsConstants.urlReferrerHashKey(urlMappingId, metricDate),
                    normalizeDimension(referrer),
                    ttl
            );

        } catch (Exception e) {
            log.warn("Failed to record live aggregates for urlId={}", urlMappingId, e);
        }
    }

    public LiveAnalyticsResponse getLiveAnalytics(Long urlMappingId) {

        try {
            LocalDate today = LocalDate.now();

            Long todayClicks = redisHelper.getCounter(
                    RedisAnalyticsConstants.urlDailyClicksKey(urlMappingId, today)
            );

            Long todayUniqueVisitors = redisHelper.getSetSize(
                    RedisAnalyticsConstants.urlUniqueVisitorsKey(urlMappingId, today)
            );

            Map<String, Long> hourlyClicks = sortedHourlyMap(
                    redisHelper.getHash(RedisAnalyticsConstants.urlHourlyClicksKey(urlMappingId, today))
            );

            Map<String, Long> countries = redisHelper.getHash(
                    RedisAnalyticsConstants.urlCountryHashKey(urlMappingId, today)
            );
            Map<String, Long> devices = redisHelper.getHash(
                    RedisAnalyticsConstants.urlDeviceHashKey(urlMappingId, today)
            );
            Map<String, Long> browsers = redisHelper.getHash(
                    RedisAnalyticsConstants.urlBrowserHashKey(urlMappingId, today)
            );
            Map<String, Long> operatingSystems = redisHelper.getHash(
                    RedisAnalyticsConstants.urlOsHashKey(urlMappingId, today)
            );
            Map<String, Long> referrers = redisHelper.getHash(
                    RedisAnalyticsConstants.urlReferrerHashKey(urlMappingId, today)
            );

            return LiveAnalyticsResponse.builder()
                    .todayClicks(todayClicks)
                    .todayUniqueVisitors(todayUniqueVisitors)
                    .hourlyClicks(hourlyClicks)
                    .countries(countries)
                    .devices(devices)
                    .browsers(browsers)
                    .operatingSystems(operatingSystems)
                    .referrers(referrers)
                    .lastUpdated(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.warn("Failed to get live analytics for urlId={}", urlMappingId, e);
            return emptyLiveResponse();
        }
    }

    public Long getRawQueueSize() {
        return analyticsEventQueue.size();
    }

    private LocalDate clickDate(ClickEventData event) {
        return event.getClickTime() != null
                ? event.getClickTime().toLocalDate()
                : LocalDate.now();
    }

    private int clickHour(ClickEventData event) {
        return event.getClickTime() != null
                ? event.getClickTime().getHour()
                : LocalDateTime.now().getHour();
    }

    private Map<String, Long> sortedHourlyMap(Map<String, Long> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        return new TreeMap<>(raw);
    }

    private String normalizeDimension(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String normalized = value.trim();

        return normalized.isBlank() ? "unknown" : normalized;
    }

    private LiveAnalyticsResponse emptyLiveResponse() {
        return LiveAnalyticsResponse.builder()
                .todayClicks(0L)
                .todayUniqueVisitors(0L)
                .hourlyClicks(Collections.emptyMap())
                .countries(Collections.emptyMap())
                .devices(Collections.emptyMap())
                .browsers(Collections.emptyMap())
                .operatingSystems(Collections.emptyMap())
                .referrers(Collections.emptyMap())
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}