package com.tinyroute.analytics.service;

import com.tinyroute.analytics.dto.ClickEventData;
import com.tinyroute.analytics.entity.ClickEvent;
import com.tinyroute.analytics.infra.RedisAnalyticsEventQueue;
import com.tinyroute.analytics.repository.ClickEventRepository;
import com.tinyroute.analytics.infra.GeoLocationService;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.analytics.infra.UserAgentParsingService;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.repository.UrlMappingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventBackgroundWorkerTest {

    @Mock
    private RedisAnalyticsEventQueue analyticsEventQueue;
    @Mock
    private ClickEventRepository clickEventRepository;
    @Mock
    private GeoLocationService geoLocationService;
    @Mock
    private UserAgentParsingService userAgentParsingService;
    @Mock
    private ClientIpService clientIpService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private UniqueVisitorRegistrationService uniqueVisitorRegistrationService;
    @Mock
    private RedisAnalyticsService redisAnalyticsService;
    @Mock
    private UrlMappingRepository urlMappingRepository;

    @InjectMocks
    private AnalyticsEventBackgroundWorker worker;

    @Test
    void processQueuedClickEvents_persistsEnrichedEventsFromQueue() {
        ClickEventData event = ClickEventData.builder()
                .urlMappingId(123L)
                .ip("1.1.1.1")
                .ipHash("hashed-ip")
                .userAgent("Mozilla/5.0")
                .referer("https://example.com")
                .language("en-US,en;q=0.9")
                .clickTime(LocalDateTime.of(2026, 5, 25, 12, 0))
                .build();

        when(analyticsEventQueue.hasEvents()).thenReturn(true);
        when(analyticsEventQueue.drainBatch(500)).thenReturn(List.of(event));

        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setId(123L);
        urlMapping.setClickCount(1L);
        when(entityManager.getReference(eq(UrlMapping.class), eq(123L))).thenReturn(urlMapping);

        when(geoLocationService.lookup("1.1.1.1"))
                .thenReturn(new GeoLocationService.GeoLocation("US", "Los Angeles"));

        when(userAgentParsingService.parse("Mozilla/5.0"))
                .thenReturn(new UserAgentParsingService.ParsedUserAgent("Chrome", "Windows", "Desktop"));

        worker.processQueuedClickEvents();

        ArgumentCaptor<List<ClickEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickEventRepository).saveAll(captor.capture());

        List<ClickEvent> saved = captor.getValue();
        assertEquals(1, saved.size());
        ClickEvent savedEvent = saved.get(0);
        assertEquals(urlMapping, savedEvent.getUrlMapping());
        assertEquals("hashed-ip", savedEvent.getIpHash());
        assertEquals("US", savedEvent.getCountry());
        assertEquals("Los Angeles", savedEvent.getCity());
        assertEquals("Chrome", savedEvent.getBrowser());
        assertEquals("Windows", savedEvent.getOs());
        assertEquals("Desktop", savedEvent.getDeviceType());
        assertEquals("https://example.com", savedEvent.getReferrer());
        assertEquals("en-US", savedEvent.getLanguage());
        assertEquals(LocalDateTime.of(2026, 5, 25, 12, 0), savedEvent.getClickDate());

        verify(uniqueVisitorRegistrationService).registerIfFirstVisit(
                123L,
                "hashed-ip",
                LocalDateTime.of(2026, 5, 25, 12, 0)
        );
        verify(redisAnalyticsService).recordLiveAggregates(
                123L,
                "US",       // country
                "Desktop",  // deviceType
                "Chrome",   // browser
                "Windows",  // os
                "https://example.com", // referrer
                LocalDate.of(2026, 5, 25)
        );
    }
}
