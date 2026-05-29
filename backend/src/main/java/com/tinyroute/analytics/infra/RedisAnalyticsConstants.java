package com.tinyroute.analytics.infra;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RedisAnalyticsConstants {

    public static final long DAILY_COUNTER_TTL_SECONDS = 2L * 24 * 60 * 60;
    public static final long UNIQUE_SET_TTL_SECONDS    = 2L * 24 * 60 * 60;

    public static final long LIVE_HASH_TTL_SECONDS = 2L * 24 * 60 * 60;

    private static final String PREFIX      = "analytics:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public static String urlDailyClicksKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":daily:" + formatDate(date);
    }

    public static String urlUniqueVisitorsKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":unique:" + formatDate(date);
    }

    public static String urlHourlyClicksKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":hourly:" + formatDate(date);
    }

    public static String urlCountryHashKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":country:" + formatDate(date);
    }

    public static String urlDeviceHashKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":device:" + formatDate(date);
    }

    public static String urlBrowserHashKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":browser:" + formatDate(date);
    }

    public static String urlOsHashKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":os:" + formatDate(date);
    }

    public static String urlReferrerHashKey(Long urlMappingId, LocalDate date) {
        return PREFIX + "url:" + urlMappingId + ":referrer:" + formatDate(date);
    }

    private static String formatDate(LocalDate date) {
        return date.format(DATE_FORMAT);
    }
}