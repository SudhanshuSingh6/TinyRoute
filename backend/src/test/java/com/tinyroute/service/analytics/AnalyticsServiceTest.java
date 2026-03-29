package com.tinyroute.service.analytics;

import com.tinyroute.entity.ClickEvent;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.mapper.AnalyticsMapper;
import com.tinyroute.repository.analytics.ClickEventRepository;
import com.tinyroute.repository.url.UrlMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private ClickEventRepository clickEventRepository;
    @Mock
    private AnalyticsMapper analyticsMapper;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getAnalytics_whenMappingMissing_returnsNull() {
        when(urlMappingRepository.findByShortUrlAndUserUsername("nope", "alice")).thenReturn(null);

        assertNull(analyticsService.getAnalytics(
                "nope", LocalDateTime.MIN, LocalDateTime.MAX, "alice"));
    }

    @Test
    void getAnalytics_whenMappingMissing_throwsNotFound() {
        when(urlMappingRepository.findByShortUrlAndUserUsername("nope", "alice"))
                .thenReturn(Optional.empty());

        assertThrows(ApiException.class, () ->
                analyticsService.getAnalytics(
                        "nope", LocalDateTime.MIN, LocalDateTime.MAX, "alice"));
    }


    void getAnalytics_whenActive_loadsClicksInRange() {
        UrlMapping mapping = new UrlMapping();
        when(urlMappingRepository.findByShortUrlAndUserUsername("abc", "alice")).thenReturn(Optional.of(mapping));
        List<ClickEvent> clicks = List.of(new ClickEvent());
        when(clickEventRepository.findByUrlMappingAndClickDateBetween(
                eq(mapping), eq(LocalDateTime.parse("2026-03-01T00:00:00")),
                eq(LocalDateTime.parse("2026-03-31T23:59:59"))))
                .thenReturn(clicks);
        when(analyticsMapper.toLinkAnalyticsResponse(clicks,7)).thenReturn(
                new com.tinyroute.dto.analytics.response.LinkAnalyticsResponse());

        var result = analyticsService.getAnalytics(
                "abc",
                LocalDateTime.parse("2026-03-01T00:00:00"),
                LocalDateTime.parse("2026-03-31T23:59:59"),
                "alice");

        assertNotNull(result);
    }

    @Test
    void getTotalClicksByUserAndDate_whenNoActiveLinks_returnsEmptyMap() {
        User user = new User();
        user.setId(1L);
        when(urlMappingRepository.findByUser(user)).thenReturn(List.of());

        Map<LocalDate, Long> totals = analyticsService.getTotalClicksByUserAndDate(
                user, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 7));

        assertTrue(totals.isEmpty());
        verify(clickEventRepository, never()).findByUrlMappingInAndClickDateBetween(
                any(), any(), any());
    }

    @Test
    void getTotalClicksByUserAndDate_usesOnlyNonDeletedMappings() {
        User user = new User();
        user.setId(1L);
        UrlMapping m = new UrlMapping();
        m.setId(5L);
        when(urlMappingRepository.findByUser(user)).thenReturn(List.of(m));

        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 2);
        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.plusDays(1).atStartOfDay();

        ClickEvent e1 = new ClickEvent();
        e1.setClickDate(LocalDateTime.of(2026, 3, 2, 10, 0));

        when(clickEventRepository.findByUrlMappingInAndClickDateBetween(
                argThat(list -> list.size() == 1 && list.get(0).getId().equals(5L)),
                eq(rangeStart),
                eq(rangeEnd)))
                .thenReturn(List.of(e1));

        Map<LocalDate, Long> totals = analyticsService.getTotalClicksByUserAndDate(user, startDate, endDate);

        assertEquals(1L, totals.get(LocalDate.of(2026, 3, 2)));
    }
}
