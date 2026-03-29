package com.tinyroute.service.url;

import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.repository.url.UrlMappingRepository;
import com.tinyroute.service.analytics.AsyncAnalyticsWorker;
import com.tinyroute.service.analytics.UniqueVisitorRegistrationService;
import com.tinyroute.service.redirect.UrlRedirectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlRedirectServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private UniqueVisitorRegistrationService uniqueVisitorRegistrationService;
    @Mock
    private AsyncAnalyticsWorker asyncAnalyticsWorker;

    @InjectMocks
    private UrlRedirectService urlRedirectService;

    @Test
    void getOriginalUrl_whenFirstVisitor_incrementsClickCountAndTriggersAsync() {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(100L);
        mapping.setShortUrl("abc12345");
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setClickCount(0);

        when(urlMappingRepository.findByShortUrl("abc12345"))
                .thenReturn(mapping);

        when(uniqueVisitorRegistrationService.registerIfFirstVisit(
                eq(100L),
                anyString(),
                any(LocalDateTime.class)
        )).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.1.1.1");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("Referer", "https://openai.com");
        request.addHeader("Accept-Language", "en-US,en;q=0.9");

        UrlMapping result = urlRedirectService.getOriginalUrl("abc12345", request);

        assertNotNull(result);
        verify(urlMappingRepository).incrementClickCount(eq(100L));
        verify(asyncAnalyticsWorker).recordClickEvent(
                eq(100L), eq("1.1.1.1"), eq("Mozilla/5.0"), eq("https://openai.com"), eq("en-US,en;q=0.9"), any()
        );
    }

    @Test
    void getOriginalUrl_whenDuplicateVisitor_skipsIncrementButTriggersAsync() {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(200L);
        mapping.setShortUrl("dup12345");
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setClickCount(10);

        when(urlMappingRepository.findByShortUrl("dup12345"))
                .thenReturn(mapping);

        when(uniqueVisitorRegistrationService.registerIfFirstVisit(
                eq(200L),
                anyString(),
                any(LocalDateTime.class)
        )).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("8.8.8.8");
        request.addHeader("User-Agent", "Mozilla/5.0");

        urlRedirectService.getOriginalUrl("dup12345", request);

        verify(urlMappingRepository, never()).incrementClickCount(anyLong());
        verify(asyncAnalyticsWorker).recordClickEvent(
                eq(200L), eq("8.8.8.8"), eq("Mozilla/5.0"), isNull(), isNull(), any()
        );
    }

    @Test
    void getOriginalUrl_whenClickLimitReached_setsStatusAndSkipsAnalytics() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortUrl("limit123");
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setClickCount(5);
        mapping.setMaxClicks(5);

        when(urlMappingRepository.findByShortUrl("limit123")).thenReturn(mapping);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UrlMapping result = urlRedirectService.getOriginalUrl("limit123", new MockHttpServletRequest());

        assertEquals(UrlStatus.CLICK_LIMIT_REACHED, result.getStatus());
        verify(asyncAnalyticsWorker, never()).recordClickEvent(any(), any(), any(), any(), any(), any());
        verify(uniqueVisitorRegistrationService, never()).registerIfFirstVisit(any(), any(), any());
    }
}
