package com.tinyroute.mapper;

import com.tinyroute.dto.analytics.AnalyticsDimensionType;
import com.tinyroute.dto.analytics.ClickByDimensionDTO;
import com.tinyroute.dto.analytics.ClickTimeBucketDTO;
import com.tinyroute.dto.analytics.ClickVelocityDTO;
import com.tinyroute.dto.analytics.DailyClickCountDTO;
import com.tinyroute.dto.analytics.PeakActivityDTO;
import com.tinyroute.dto.analytics.TimeBucketType;
import com.tinyroute.dto.analytics.response.LinkAnalyticsResponse;
import com.tinyroute.entity.ClickEvent;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AnalyticsMapper {

    public LinkAnalyticsResponse toLinkAnalyticsResponse(List<ClickEvent> clicks,
                                                         long uniqueClicks,
                                                         LocalDateTime start,
                                                         LocalDateTime end) {
        LinkAnalyticsResponse dto = new LinkAnalyticsResponse();
        dto.setTotalClicks(clicks.size());
        dto.setUniqueClicks(uniqueClicks);
        dto.setClicksByDimension(buildDimensionBreakdown(clicks));
        dto.setClicksByTimeBucket(buildTimeBuckets(clicks, start, end));
        dto.setDailyClicks(buildDailyCounts(clicks));
        dto.setPeakActivity(buildPeakActivity(clicks, start, end));
        dto.setClickVelocity(buildVelocity(clicks, start, end));
        return dto;
    }

    public LinkAnalyticsResponse toLinkAnalyticsResponse(List<ClickEvent> clicks, long uniqueClicks) {
        return toLinkAnalyticsResponse(clicks, uniqueClicks, null, null);
    }

    private List<ClickByDimensionDTO> buildDimensionBreakdown(List<ClickEvent> clicks) {
        List<ClickByDimensionDTO> result = new ArrayList<>();

        result.addAll(toDimensionRows(
                AnalyticsDimensionType.COUNTRY,
                clicks,
                c -> valueOrFallback(c.getCountry(), "Unknown")
        ));

        result.addAll(toDimensionRows(
                AnalyticsDimensionType.DEVICE,
                clicks,
                c -> valueOrFallback(c.getDeviceType(), "Unknown")
        ));

        result.addAll(toDimensionRows(
                AnalyticsDimensionType.BROWSER,
                clicks,
                c -> valueOrFallback(c.getBrowser(), "Unknown")
        ));

        result.addAll(toDimensionRows(
                AnalyticsDimensionType.OS,
                clicks,
                c -> valueOrFallback(c.getOs(), "Unknown")
        ));

        result.addAll(toDimensionRows(
                AnalyticsDimensionType.REFERRER,
                clicks,
                c -> normalizeReferrer(c.getReferrer())
        ));

        return result;
    }

    private List<ClickByDimensionDTO> toDimensionRows(AnalyticsDimensionType dimension,
                                                      List<ClickEvent> clicks,
                                                      Function<ClickEvent, String> classifier) {
        return clicks.stream()
                .collect(Collectors.groupingBy(classifier, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new ClickByDimensionDTO(dimension, e.getKey(), e.getValue()))
                .toList();
    }

    private List<ClickTimeBucketDTO> buildTimeBuckets(List<ClickEvent> clicks,
                                                      LocalDateTime start,
                                                      LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return List.of();
        }

        TimeBucketType type = resolveTimeBucketType(start, end);

        Map<String, Long> grouped;

        if (type == TimeBucketType.HOUR) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
            grouped = new TreeMap<>(clicks.stream()
                    .collect(Collectors.groupingBy(
                            click -> click.getClickDate()
                                    .withMinute(0)
                                    .withSecond(0)
                                    .withNano(0)
                                    .format(formatter),
                            Collectors.counting()
                    )));
        } else if (type == TimeBucketType.DAY) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            grouped = new TreeMap<>(clicks.stream()
                    .collect(Collectors.groupingBy(
                            click -> click.getClickDate().toLocalDate().format(formatter),
                            Collectors.counting()
                    )));
        } else if (type == TimeBucketType.WEEK) {
            grouped = new TreeMap<>(clicks.stream()
                    .collect(Collectors.groupingBy(
                            click -> {
                                LocalDate weekStart = click.getClickDate()
                                        .toLocalDate()
                                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                                return "Week of " + weekStart;
                            },
                            Collectors.counting()
                    )));
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
            grouped = new TreeMap<>(clicks.stream()
                    .collect(Collectors.groupingBy(
                            click -> click.getClickDate().format(formatter),
                            Collectors.counting()
                    )));
        }

        return grouped.entrySet().stream()
                .map(entry -> new ClickTimeBucketDTO(type, entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DailyClickCountDTO> buildDailyCounts(List<ClickEvent> clicks) {
        return clicks.stream()
                .collect(Collectors.groupingBy(
                        click -> click.getClickDate().toLocalDate(),
                        TreeMap::new,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .map(entry -> new DailyClickCountDTO(entry.getKey(), entry.getValue()))
                .toList();
    }

    private PeakActivityDTO buildPeakActivity(List<ClickEvent> clicks,
                                              LocalDateTime start,
                                              LocalDateTime end) {
        if (clicks == null || clicks.isEmpty() || start == null || end == null || !end.isAfter(start)) {
            return new PeakActivityDTO(TimeBucketType.HOUR, "N/A", 0);
        }

        TimeBucketType type = resolveTimeBucketType(start, end);

        return switch (type) {
            case HOUR -> buildPeakHour(clicks);
            case DAY -> buildPeakDay(clicks);
            case WEEK -> buildPeakWeek(clicks);
            case MONTH -> buildPeakMonth(clicks);
        };
    }
    private PeakActivityDTO buildPeakHour(List<ClickEvent> clicks) {
        Map<String, Long> byHour = clicks.stream()
                .collect(Collectors.groupingBy(
                        click -> click.getClickDate()
                                .withMinute(0)
                                .withSecond(0)
                                .withNano(0)
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")),
                        Collectors.counting()
                ));

        Map.Entry<String, Long> peak = byHour.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry::getKey))
                .orElse(null);

        if (peak == null) {
            return new PeakActivityDTO(TimeBucketType.HOUR, "N/A", 0);
        }

        return new PeakActivityDTO(TimeBucketType.HOUR, peak.getKey(), peak.getValue());
    }

    private PeakActivityDTO buildPeakDay(List<ClickEvent> clicks) {
        Map<String, Long> byDay = clicks.stream()
                .collect(Collectors.groupingBy(
                        click -> click.getClickDate().toLocalDate().toString(),
                        Collectors.counting()
                ));

        Map.Entry<String, Long> peak = byDay.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry::getKey))
                .orElse(null);

        if (peak == null) {
            return new PeakActivityDTO(TimeBucketType.DAY, "N/A", 0);
        }

        return new PeakActivityDTO(TimeBucketType.DAY, peak.getKey(), peak.getValue());
    }

    private PeakActivityDTO buildPeakWeek(List<ClickEvent> clicks) {
        Map<String, Long> byWeek = clicks.stream()
                .collect(Collectors.groupingBy(
                        click -> {
                            LocalDate weekStart = click.getClickDate()
                                    .toLocalDate()
                                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                            return "Week of " + weekStart;
                        },
                        Collectors.counting()
                ));

        Map.Entry<String, Long> peak = byWeek.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry::getKey))
                .orElse(null);

        if (peak == null) {
            return new PeakActivityDTO(TimeBucketType.WEEK, "N/A", 0);
        }

        return new PeakActivityDTO(TimeBucketType.WEEK, peak.getKey(), peak.getValue());
    }

    private PeakActivityDTO buildPeakMonth(List<ClickEvent> clicks) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        Map<String, Long> byMonth = clicks.stream()
                .collect(Collectors.groupingBy(
                        click -> click.getClickDate().format(formatter),
                        Collectors.counting()
                ));

        Map.Entry<String, Long> peak = byMonth.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry::getKey))
                .orElse(null);

        if (peak == null) {
            return new PeakActivityDTO(TimeBucketType.MONTH, "N/A", 0);
        }

        return new PeakActivityDTO(TimeBucketType.MONTH, peak.getKey(), peak.getValue());
    }

    private TimeBucketType resolveTimeBucketType(LocalDateTime start, LocalDateTime end) {
        long hours = Duration.between(start, end).toHours();
        long days = Duration.between(start, end).toDays();

        if (hours <= 48) {
            return TimeBucketType.HOUR;
        }
        if (days <= 60) {
            return TimeBucketType.DAY;
        }
        if (days <= 365) {
            return TimeBucketType.WEEK;
        }
        return TimeBucketType.MONTH;
    }

    private ClickVelocityDTO buildVelocity(List<ClickEvent> clicks,
                                           LocalDateTime start,
                                           LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return new ClickVelocityDTO(0, 0, "STABLE");
        }

        long totalSeconds = Duration.between(start, end).getSeconds();

        if (totalSeconds <= 3600) {
            long count = clicks.size();
            return new ClickVelocityDTO(count, 0, count > 0 ? "UP" : "STABLE");
        }

        LocalDateTime midpoint = start.plusSeconds(totalSeconds / 2);

        long firstHalf = clicks.stream()
                .filter(c -> !c.getClickDate().isBefore(start) && c.getClickDate().isBefore(midpoint))
                .count();

        long secondHalf = clicks.stream()
                .filter(c -> !c.getClickDate().isBefore(midpoint) && !c.getClickDate().isAfter(end))
                .count();

        String trend = secondHalf > firstHalf
                ? "UP"
                : secondHalf < firstHalf
                ? "DOWN"
                : "STABLE";

        return new ClickVelocityDTO(secondHalf, firstHalf, trend);
    }

    private String normalizeReferrer(String referrer) {
        if (referrer == null || referrer.isBlank() || referrer.equalsIgnoreCase("Direct")) {
            return "Direct";
        }

        String lower = referrer.toLowerCase();

        if (lower.contains("google")) return "Google";
        if (lower.contains("linkedin")) return "LinkedIn";
        if (lower.contains("twitter") || lower.contains("x.com")) return "Twitter/X";
        if (lower.contains("github")) return "GitHub";
        if (lower.contains("reddit")) return "Reddit";
        if (lower.contains("facebook")) return "Facebook";
        if (lower.contains("instagram")) return "Instagram";
        if (lower.contains("youtube")) return "YouTube";

        return "Other";
    }

    private String valueOrFallback(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}