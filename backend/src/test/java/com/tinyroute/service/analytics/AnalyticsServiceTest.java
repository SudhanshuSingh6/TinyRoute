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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private ClickEventRepository clickEventRepository;
    @Mock
    private UrlUniqueVisitorRepository urlUniqueVisitorRepository;
    @Mock
    private AnalyticsMapper analyticsMapper;

    @InjectMocks
    private AnalyticsService analyticsService;

    // ─── helpers ────────────────────────────────────────────────────────────────

    private AnalyticsQueryRequest queryRequest(LocalDateTime start, LocalDateTime end) {
        AnalyticsQueryRequest req = new AnalyticsQueryRequest();
        req.setStartDate(start);
        req.setEndDate(end);
        return req;
    }

    private UrlMapping activeMapping(Long id) {
        UrlMapping m = new UrlMapping();
        m.setId(id);
        m.setCreatedDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        return m;
    }

    // ─── getAnalytics: link not found ───────────────────────────────────────────

    @Test
    void getAnalytics_whenMappingMissing_throwsNotFound() {
        when(urlMappingRepository.findByShortUrlAndUserUsername("nope", "alice"))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                analyticsService.getAnalytics("nope", queryRequest(
                        LocalDateTime.of(2026, 3, 1, 0, 0),
                        LocalDateTime.of(2026, 3, 31, 23, 59)
                ), "alice"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        assertEquals("URL_NOT_FOUND", ex.getErrorCode());
    }

    // ─── getAnalytics: invalid date range ───────────────────────────────────────

    @Test
    void getAnalytics_whenEndBeforeStart_throwsBadRequest() {
        UrlMapping mapping = activeMapping(1L);
        when(urlMappingRepository.findByShortUrlAndUserUsername("abc", "alice"))
                .thenReturn(Optional.of(mapping));

        // end is before start
        AnalyticsQueryRequest req = queryRequest(
                LocalDateTime.of(2026, 3, 31, 0, 0),
                LocalDateTime.of(2026, 3, 1, 0, 0)
        );

        ApiException ex = assertThrows(ApiException.class,
                () -> analyticsService.getAnalytics("abc", req, "alice"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals("INVALID_DATE_RANGE", ex.getErrorCode());
    }

    @Test
    void getAnalytics_whenEndEqualsStart_throwsBadRequest() {
        UrlMapping mapping = activeMapping(1L);
        when(urlMappingRepository.findByShortUrlAndUserUsername("abc", "alice"))
                .thenReturn(Optional.of(mapping));

        LocalDateTime same = LocalDateTime.of(2026, 3, 15, 12, 0);
        AnalyticsQueryRequest req = queryRequest(same, same);

        assertThrows(ApiException.class,
                () -> analyticsService.getAnalytics("abc", req, "alice"));
    }

    // ─── getAnalytics: successful response ──────────────────────────────────────

    @Test
    void getAnalytics_whenActive_loadsClicksInRange() {
        UrlMapping mapping = activeMapping(2L);
        when(urlMappingRepository.findByShortUrlAndUserUsername("abc", "alice"))
                .thenReturn(Optional.of(mapping));

        LocalDateTime start = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 31, 23, 59);
        List<ClickEvent> clicks = List.of(new ClickEvent());

        when(clickEventRepository.findByUrlMappingAndClickDateBetween(mapping, start, end))
                .thenReturn(clicks);
        when(urlUniqueVisitorRepository.countByUrlMappingIdAndFirstSeenAtBetween(2L, start, end))
                .thenReturn(1L);
        when(analyticsMapper.toLinkAnalyticsResponse(eq(clicks), eq(1L), eq(start), eq(end)))
                .thenReturn(new LinkAnalyticsResponse());

        LinkAnalyticsResponse result = analyticsService.getAnalytics("abc", queryRequest(start, end), "alice");

        assertNotNull(result);
        verify(clickEventRepository).findByUrlMappingAndClickDateBetween(mapping, start, end);
        verify(urlUniqueVisitorRepository).countByUrlMappingIdAndFirstSeenAtBetween(2L, start, end);
    }

    @Test
    void getAnalytics_whenNoStartDateProvided_usesCreatedDate() {
        UrlMapping mapping = activeMapping(3L);
        LocalDateTime createdDate = LocalDateTime.of(2026, 1, 1, 0, 0);
        mapping.setCreatedDate(createdDate);
        when(urlMappingRepository.findByShortUrlAndUserUsername("abc", "alice"))
                .thenReturn(Optional.of(mapping));

        LocalDateTime end = LocalDateTime.now().minusDays(1);
        when(clickEventRepository.findByUrlMappingAndClickDateBetween(eq(mapping), eq(createdDate), any()))
                .thenReturn(List.of());
        when(urlUniqueVisitorRepository.countByUrlMappingIdAndFirstSeenAtBetween(any(), any(), any()))
                .thenReturn(0L);
        when(analyticsMapper.toLinkAnalyticsResponse(any(), anyLong(), any(), any()))
                .thenReturn(new LinkAnalyticsResponse());

        // no start date in request → should default to createdDate
        analyticsService.getAnalytics("abc", queryRequest(null, end), "alice");

        verify(clickEventRepository).findByUrlMappingAndClickDateBetween(eq(mapping), eq(createdDate), any());
    }

    // ─── getTotalClicksByUserAndDate ─────────────────────────────────────────────

    @Test
    void getTotalClicksByUserAndDate_whenNoLinks_returnsEmptyMap() {
        User user = new User();
        user.setId(1L);
        when(urlMappingRepository.findByUser(user)).thenReturn(List.of());

        Map<LocalDate, Long> totals = analyticsService.getTotalClicksByUserAndDate(
                user, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 7));

        assertTrue(totals.isEmpty());
        // No DB query for clicks when there are no mappings
        verify(clickEventRepository, never()).findByUrlMappingInAndClickDateBetween(any(), any(), any());
    }

    @Test
    void getTotalClicksByUserAndDate_groupsClicksByDay() {
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
        ClickEvent e2 = new ClickEvent();
        e2.setClickDate(LocalDateTime.of(2026, 3, 2, 14, 0));

        when(clickEventRepository.findByUrlMappingInAndClickDateBetween(
                argThat(list -> list.size() == 1 && Long.valueOf(5L).equals(list.get(0).getId())),
                eq(rangeStart), eq(rangeEnd)))
                .thenReturn(List.of(e1, e2));

        Map<LocalDate, Long> totals = analyticsService.getTotalClicksByUserAndDate(user, startDate, endDate);

        // Both clicks are on March 2
        assertEquals(2L, totals.get(LocalDate.of(2026, 3, 2)));
        assertNull(totals.get(LocalDate.of(2026, 3, 1)));
    }

    @Test
    void getTotalClicksByUserAndDate_withClicksOnDifferentDays_groupsCorrectly() {
        User user = new User();
        user.setId(2L);
        UrlMapping m = new UrlMapping();
        m.setId(6L);
        when(urlMappingRepository.findByUser(user)).thenReturn(List.of(m));

        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 3);

        ClickEvent day1 = new ClickEvent();
        day1.setClickDate(LocalDateTime.of(2026, 3, 1, 9, 0));
        ClickEvent day2a = new ClickEvent();
        day2a.setClickDate(LocalDateTime.of(2026, 3, 2, 11, 0));
        ClickEvent day2b = new ClickEvent();
        day2b.setClickDate(LocalDateTime.of(2026, 3, 2, 18, 0));

        when(clickEventRepository.findByUrlMappingInAndClickDateBetween(any(), any(), any()))
                .thenReturn(List.of(day1, day2a, day2b));

        Map<LocalDate, Long> totals = analyticsService.getTotalClicksByUserAndDate(user, startDate, endDate);

        assertEquals(1L, totals.get(LocalDate.of(2026, 3, 1)));
        assertEquals(2L, totals.get(LocalDate.of(2026, 3, 2)));
        assertNull(totals.get(LocalDate.of(2026, 3, 3))); // no clicks that day
    }

    // ─── getAllTimeUniqueClicks ───────────────────────────────────────────────────

    @Test
    void getAllTimeUniqueClicks_whenLinkExists_returnsClickCount() {
        UrlMapping mapping = activeMapping(10L);
        mapping.setClickCount(42);
        when(urlMappingRepository.findByShortUrlAndUserUsername("link1", "alice"))
                .thenReturn(Optional.of(mapping));

        long count = analyticsService.getAllTimeUniqueClicks("link1", "alice");

        assertEquals(42L, count);
    }

    @Test
    void getAllTimeUniqueClicks_whenLinkMissing_throwsNotFound() {
        when(urlMappingRepository.findByShortUrlAndUserUsername("missing", "alice"))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> analyticsService.getAllTimeUniqueClicks("missing", "alice"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }
}
