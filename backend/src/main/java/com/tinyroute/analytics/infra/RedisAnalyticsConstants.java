package com.tinyroute.analytics.infra;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RedisAnalyticsConstants {

    private static final String PREFIX = "analytics:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    // Replace the TTL block with:
    public static final long DAILY_COUNTER_TTL_SECONDS = 30L * 24 * 60 * 60;   // 30 days
    public static final long UNIQUE_SET_TTL_SECONDS  = 30L * 24 * 60 * 60;   // 30 days
    public static final long TOTAL_COUNTER_TTL_SECONDS = 315360000L;        // 10 years (effectively permanent)

    // ═══════════════════════════════════════════════════════════
    // Global Counters
    // ═══════════════════════════════════════════════════════════

    public static String globalClicksKey() {
        return PREFIX + "global:clicks";
    }

    public static String globalDailyKey(LocalDate date) {
        return PREFIX + "global:daily:" + formatDate(date);
    }

    // ═══════════════════════════════════════════════════════════
    // Per-URL Counters
    // ═══════════════════════════════════════════════════════════

    /**
     * Total clicks all-time for URL
     * Example: analytics:url:123:total
     */
    public static String urlTotalClicksKey(Long urlMappingId) {
        return PREFIX + "url:" + urlMappingId + ":total";
    }

    /**
     * Daily clicks counter
     * Example: analytics:url:123:daily:2026-05-24
     */
    public static String urlDailyClicksKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":daily:" + formatDate(date);
    }

    /**
     * Unique visitors set (for deduplication)
     * Example: analytics:url:123:unique:2026-05-24
     */
    public static String urlUniqueVisitorsKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":unique:" + formatDate(date);
    }

    // ═══════════════════════════════════════════════════════════
    // Device Breakdown
    // ═══════════════════════════════════════════════════════════

    /**
     * Device type counter
     * Example: analytics:url:123:device:mobile:2026-05-24
     */
    public static String urlDeviceKey(Long urlMappingId, String deviceType, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":device:" + deviceType + ":" + formatDate(date);
    }

    /**
     * Browser counter
     * Example: analytics:url:123:browser:chrome:2026-05-24
     */
    public static String urlBrowserKey(Long urlMappingId, String browser, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":browser:" + browser + ":" + formatDate(date);
    }

    /**
     * Country counter
     * Example: analytics:url:123:country:in:2026-05-24
     */
    public static String urlCountryKey(Long urlMappingId, String country, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":country:" + country + ":" + formatDate(date);
    }


    private static String formatDate(LocalDate date) {
        return date.format(DATE_FORMAT);
    }
}