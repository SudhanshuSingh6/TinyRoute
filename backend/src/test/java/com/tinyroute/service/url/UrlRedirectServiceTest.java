package com.tinyroute.service.url;

import com.tinyroute.analytics.dto.ClickEventData;
import com.tinyroute.analytics.infra.ClickEventDataBuilder;
import com.tinyroute.analytics.service.RedisAnalyticsService;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.entity.UrlStatus;
import com.tinyroute.url.repository.UrlMappingRepository;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.infra.cache.RedirectCacheService;
import com.tinyroute.service.redirect.UrlRedirectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlRedirectServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private ClientIpService clientIpService;
    @Mock
    private RedirectCacheService redirectCacheService;
    @Mock
    private RedisAnalyticsService redisAnalyticsService;
    @Mock
    private ClickEventDataBuilder clickEventDataBuilder;

    @InjectMocks
    private UrlRedirectService urlRedirectService;

    // ─── helper ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        when(redirectCacheService.get(anyString())).thenReturn(Optional.empty());
    }

    private MockHttpServletRequest requestFrom(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        req.addHeader("User-Agent", "Mozilla/5.0");
        req.addHeader("Referer", "https://openai.com");
        req.addHeader("Accept-Language", "en-US,en;q=0.9");
        return req;
    }

    private UrlMapping activeMapping(long id, String shortUrl) {
        UrlMapping m = new UrlMapping();
        m.setId(id);
        m.setShortUrl(shortUrl);
        m.setStatus(UrlStatus.ACTIVE);
        m.setClickCount(0);
        return m;
    }

    private ClickEventData stubRecordedClick(UrlMapping mapping, MockHttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String ipHash = "hash-" + ip;
        ClickEventData event = ClickEventData.builder()
                .urlMappingId(mapping.getId())
                .ip(ip)
                .ipHash(ipHash)
                .userAgent(request.getHeader("User-Agent"))
                .referer(request.getHeader("Referer"))
                .language(request.getHeader("Accept-Language"))
                .clickTime(LocalDateTime.now())
                .build();

        when(clientIpService.resolveClientIp(request)).thenReturn(ip);
        when(clientIpService.hashIp(ip)).thenReturn(ipHash);
        when(clickEventDataBuilder.buildFromRequest(
                eq(mapping.getId()),
                eq(request),
                eq(ip),
                eq(ipHash),
                eq(request.getHeader("User-Agent")),
                any(LocalDateTime.class)
        )).thenReturn(event);
        when(redisAnalyticsService.recordClick(mapping, event)).thenReturn(true);

        return event;
    }

    // ─── Short URL not found ────────────────────────────────────────────────────

    @Test
    void getOriginalUrl_whenShortUrlNotFound_returnsNull() {
        when(urlMappingRepository.findByShortUrl("missing")).thenReturn(null);

        UrlMapping result = urlRedirectService.getOriginalUrl("missing", requestFrom("1.1.1.1"));

        assertNull(result);
        verifyNoInteractions(clickEventDataBuilder);
        verify(redisAnalyticsService, never()).recordClick(any(), any());
    }

    // ─── Disabled link ──────────────────────────────────────────────────────────

    @Test
    void getOriginalUrl_whenDisabled_returnsImmediatelyWithoutAnalytics() {
        UrlMapping mapping = activeMapping(10L, "dis12345");
        mapping.setStatus(UrlStatus.DISABLED);
        when(urlMappingRepository.findByShortUrl("dis12345")).thenReturn(mapping);

        UrlMapping result = urlRedirectService.getOriginalUrl("dis12345", requestFrom("1.1.1.1"));

        assertEquals(UrlStatus.DISABLED, result.getStatus());
        verifyNoInteractions(clickEventDataBuilder);
        verify(redisAnalyticsService, never()).recordClick(any(), any());
    }

    // ─── Expired link ───────────────────────────────────────────────────────────

    @Test
    void getOriginalUrl_whenExpired_setsStatusAndSkipsAnalytics() {
        UrlMapping mapping = activeMapping(20L, "exp12345");
        mapping.setExpiresAt(LocalDateTime.now().minusDays(1)); // already expired

        when(urlMappingRepository.findByShortUrl("exp12345")).thenReturn(mapping);

        UrlMapping result = urlRedirectService.getOriginalUrl("exp12345", requestFrom("1.1.1.1"));

        assertEquals(UrlStatus.EXPIRED, result.getStatus());
        verify(urlMappingRepository).updateStatus(eq(20L), eq(UrlStatus.EXPIRED));
        verifyNoInteractions(clickEventDataBuilder);
        verify(redisAnalyticsService, never()).recordClick(any(), any());
    }

    @Test
    void getOriginalUrl_whenAlreadyExpiredInDb_doesNotCallUpdateStatusAgain() {
        UrlMapping mapping = activeMapping(21L, "exp22222");
        mapping.setStatus(UrlStatus.EXPIRED); // already marked in DB
        mapping.setExpiresAt(LocalDateTime.now().minusDays(2));

        when(urlMappingRepository.findByShortUrl("exp22222")).thenReturn(mapping);

        urlRedirectService.getOriginalUrl("exp22222", requestFrom("1.1.1.1"));

        // Already EXPIRED — no redundant status update
        verify(urlMappingRepository, never()).updateStatus(anyLong(), any());
        verifyNoInteractions(clickEventDataBuilder);
        verify(redisAnalyticsService, never()).recordClick(any(), any());
    }

    // ─── Click limit already reached ────────────────────────────────────────────

    @Test
    void getOriginalUrl_whenClickLimitAlreadyReached_setsStatusAndSkipsAnalytics() {
        UrlMapping mapping = activeMapping(30L, "limit123");
        mapping.setClickCount(5);
        mapping.setMaxClicks(5);

        when(urlMappingRepository.findByShortUrl("limit123")).thenReturn(mapping);
        when(redisAnalyticsService.getUrlTotalClicks(30L)).thenReturn(0L);

        UrlMapping result = urlRedirectService.getOriginalUrl("limit123", new MockHttpServletRequest());

        assertEquals(UrlStatus.CLICK_LIMIT_REACHED, result.getStatus());
        // Service must call updateStatus — NOT save()
        verify(urlMappingRepository).updateStatus(eq(30L), eq(UrlStatus.CLICK_LIMIT_REACHED));
        verify(urlMappingRepository, never()).save(any());
        verifyNoInteractions(clickEventDataBuilder);
        verify(redisAnalyticsService, never()).recordClick(any(), any());
    }

    @Test
    void getOriginalUrl_whenStatusAlreadyClickLimitReached_doesNotCallUpdateStatusAgain() {
        UrlMapping mapping = activeMapping(31L, "limitx22");
        mapping.setStatus(UrlStatus.CLICK_LIMIT_REACHED); // already in DB
        mapping.setClickCount(10);
        mapping.setMaxClicks(10);

        when(urlMappingRepository.findByShortUrl("limitx22")).thenReturn(mapping);
        when(redisAnalyticsService.getUrlTotalClicks(31L)).thenReturn(0L);

        urlRedirectService.getOriginalUrl("limitx22", new MockHttpServletRequest());

        verify(urlMappingRepository, never()).updateStatus(anyLong(), any());
        verifyNoInteractions(clickEventDataBuilder);
        verify(redisAnalyticsService, never()).recordClick(any(), any());
    }

    // ─── First (unique) visitor ─────────────────────────────────────────────────

    @Test
    void getOriginalUrl_whenFirstVisitor_incrementsClickCountAndTriggersAsync() {
        UrlMapping mapping = activeMapping(100L, "abc12345");

        when(urlMappingRepository.findByShortUrl("abc12345")).thenReturn(mapping);

        MockHttpServletRequest request = requestFrom("1.1.1.1");
        ClickEventData event = stubRecordedClick(mapping, request);
        UrlMapping result = urlRedirectService.getOriginalUrl("abc12345", request);

        assertNotNull(result);
        verify(redisAnalyticsService).recordClick(mapping, event);
        verify(urlMappingRepository, never()).incrementClickCount(anyLong());
        verify(urlMappingRepository, never()).updateStatus(eq(100L), eq(UrlStatus.CLICK_LIMIT_REACHED));
    }

    // ─── Duplicate (non-unique) visitor ─────────────────────────────────────────

    @Test
    void getOriginalUrl_whenDuplicateVisitor_skipsIncrementButTriggersAsync() {
        UrlMapping mapping = activeMapping(200L, "dup12345");
        mapping.setClickCount(10);

        when(urlMappingRepository.findByShortUrl("dup12345")).thenReturn(mapping);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("8.8.8.8");
        request.addHeader("User-Agent", "Mozilla/5.0");

        ClickEventData event = stubRecordedClick(mapping, request);

        urlRedirectService.getOriginalUrl("dup12345", request);

        verify(redisAnalyticsService).recordClick(mapping, event);
        verify(urlMappingRepository, never()).incrementClickCount(anyLong());
    }

    // ─── Click limit reached on THIS unique visit ────────────────────────────────

    @Test
    void getOriginalUrl_whenUniqueClickReachesLimit_allowsCurrentRequestAndDefersBlocking() {
        UrlMapping mapping = activeMapping(300L, "edge1234");
        mapping.setClickCount(4);   // one away from limit
        mapping.setMaxClicks(5);

        when(urlMappingRepository.findByShortUrl("edge1234")).thenReturn(mapping);
        when(redisAnalyticsService.getUrlTotalClicks(300L)).thenReturn(0L);

        MockHttpServletRequest req = requestFrom("5.5.5.5");
        ClickEventData event = stubRecordedClick(mapping, req);
        urlRedirectService.getOriginalUrl("edge1234", req);

        verify(redisAnalyticsService).recordClick(mapping, event);
        // Boundary rule: reaching max on this click still allows current redirect.
        // Blocking should start from the next request.
        verify(urlMappingRepository, never()).updateStatus(eq(300L), eq(UrlStatus.CLICK_LIMIT_REACHED));
        assertEquals(4, mapping.getClickCount());
        assertEquals(UrlStatus.ACTIVE, mapping.getStatus());
    }

    // ─── No expiry / no maxClicks ────────────────────────────────────────────────

    @Test
    void getOriginalUrl_whenNoExpiryAndNoMaxClicks_alwaysRedirects() {
        UrlMapping mapping = activeMapping(400L, "free1234");
        mapping.setClickCount(999);
        mapping.setMaxClicks(null);
        mapping.setExpiresAt(null);

        when(urlMappingRepository.findByShortUrl("free1234")).thenReturn(mapping);

        MockHttpServletRequest req = requestFrom("2.2.2.2");
        stubRecordedClick(mapping, req);
        UrlMapping result = urlRedirectService.getOriginalUrl("free1234", req);

        assertEquals(UrlStatus.ACTIVE, result.getStatus());
        verify(urlMappingRepository, never()).updateStatus(anyLong(), any());
    }

    // ─── Not-yet-expired link ───────────────────────────────────────────────────

    @Test
    void getOriginalUrl_whenExpiryInFuture_redirectsNormally() {
        UrlMapping mapping = activeMapping(500L, "futx1234");
        mapping.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(urlMappingRepository.findByShortUrl("futx1234")).thenReturn(mapping);

        MockHttpServletRequest req = requestFrom("3.3.3.3");
        stubRecordedClick(mapping, req);
        UrlMapping result = urlRedirectService.getOriginalUrl("futx1234", req);

        assertEquals(UrlStatus.ACTIVE, result.getStatus());
        verify(urlMappingRepository, never()).updateStatus(anyLong(), eq(UrlStatus.EXPIRED));
    }
}
