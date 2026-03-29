package com.tinyroute.service.analytics;

import com.tinyroute.dto.analytics.request.AnalyticsQueryRequest;
import com.tinyroute.dto.analytics.response.LinkAnalyticsResponse;
import com.tinyroute.entity.ClickEvent;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.mapper.AnalyticsMapper;
import com.tinyroute.repository.analytics.ClickEventRepository;
import com.tinyroute.repository.analytics.UrlUniqueVisitorRepository;
import com.tinyroute.repository.url.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UrlMappingRepository urlMappingRepository;
    private final ClickEventRepository clickEventRepository;
    private final UrlUniqueVisitorRepository urlUniqueVisitorRepository;
    private final AnalyticsMapper analyticsMapper;

    public LinkAnalyticsResponse getAnalytics(String shortUrl,
                                              AnalyticsQueryRequest request,
                                              String username) {

        UrlMapping urlMapping = urlMappingRepository
                .findByShortUrlAndUserUsername(shortUrl, username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "LINK_NOT_FOUND",
                        "Link not found."
                ));

        LocalDateTime start = request.getStartDate() != null
                ? request.getStartDate()
                : urlMapping.getCreatedDate();

        LocalDateTime end = request.getEndDate() != null
                ? request.getEndDate()
                : LocalDateTime.now();

        if (end.isAfter(LocalDateTime.now())) {
            end = LocalDateTime.now();
        }

        if (!end.isAfter(start)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "endDate must be after startDate."
            );
        }

        List<ClickEvent> clicks = clickEventRepository
                .findByUrlMappingAndClickDateBetween(urlMapping, start, end);

        long uniqueClicks = urlUniqueVisitorRepository
                .countByUrlMappingIdAndFirstSeenAtBetween(urlMapping.getId(), start, end);

        return analyticsMapper.toLinkAnalyticsResponse(clicks, uniqueClicks, start, end);
    }

    public Map<LocalDate, Long> getTotalClicksByUserAndDate(User user,
                                                            LocalDate start,
                                                            LocalDate end) {
        List<UrlMapping> urlMappings = urlMappingRepository.findByUser(user);

        if (urlMappings.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ClickEvent> clickEvents =
                clickEventRepository.findByUrlMappingInAndClickDateBetween(
                        urlMappings,
                        start.atStartOfDay(),
                        end.plusDays(1).atStartOfDay()
                );

        return clickEvents.stream().collect(Collectors.groupingBy(
                click -> click.getClickDate().toLocalDate(),
                Collectors.counting()
        ));
    }

    public long getAllTimeUniqueClicks(String shortUrl, String username) {
        UrlMapping urlMapping = urlMappingRepository
                .findByShortUrlAndUserUsername(shortUrl, username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "LINK_NOT_FOUND",
                        "Link not found."
                ));

        return urlMapping.getClickCount();
    }
}