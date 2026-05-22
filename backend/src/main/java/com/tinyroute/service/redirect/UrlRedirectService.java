package com.tinyroute.service.redirect;

import com.tinyroute.common.time.DateTimeUtil;
import com.tinyroute.infra.cache.RedirectCacheEntry;
import com.tinyroute.infra.cache.RedirectCacheService;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.repository.url.UrlMappingRepository;
import com.tinyroute.service.analytics.AsyncAnalyticsWorker;
import com.tinyroute.service.analytics.UniqueVisitorRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlRedirectService {

    private final UrlMappingRepository urlMappingRepository;
    private final UniqueVisitorRegistrationService uniqueVisitorRegistrationService;
    private final AsyncAnalyticsWorker asyncAnalyticsWorker;
    private final ClientIpService clientIpService;
    private final RedirectCacheService redirectCacheService;

    @Transactional
    public UrlMapping getOriginalUrl(String shortUrl, HttpServletRequest request) {

        UrlMapping urlMapping;

        Optional<RedirectCacheEntry> cached = redirectCacheService.get(shortUrl);

        if (cached.isPresent()) {
            urlMapping = redirectCacheService.toUrlMapping(cached.get());
            log.debug("Cache HIT for '{}'", shortUrl);
        } else {
            urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
            if (urlMapping == null) {
                return null;
            }
            redirectCacheService.put(shortUrl, urlMapping);
            log.debug("Cache MISS for '{}' — populated from Postgres", shortUrl);
        }

        LocalDateTime now = LocalDateTime.now();
        UrlStatus resolvedStatus = resolveStatus(urlMapping);

        if (urlMapping.getStatus() != resolvedStatus) {
            urlMappingRepository.updateStatus(urlMapping.getId(), resolvedStatus);
            redirectCacheService.evict(shortUrl);
        }

        if (resolvedStatus != UrlStatus.ACTIVE) {
            return urlMapping;
        }

        String ip = clientIpService.resolveClientIp(request);
        String ipHash = clientIpService.hashIp(ip);

        boolean isFirstVisit = uniqueVisitorRegistrationService
                .registerIfFirstVisit(urlMapping.getId(), ipHash, now);

        urlMappingRepository.updateLastClickedAt(urlMapping.getId(), now);

        if (isFirstVisit) {
            urlMappingRepository.incrementClickCount(urlMapping.getId());

            int newClickCount = urlMapping.getClickCount() + 1;

            if (urlMapping.getMaxClicks() != null
                    && newClickCount >= urlMapping.getMaxClicks()) {
                redirectCacheService.evict(shortUrl);
            }
        }

        asyncAnalyticsWorker.recordClickEvent(
                urlMapping.getId(),                      // ← ID only, not entity
                ip,
                request.getHeader("User-Agent"),
                request.getHeader("Referer"),
                request.getHeader("Accept-Language"),
                now
        );

        return urlMapping;
    }


    private UrlStatus resolveStatus(UrlMapping urlMapping) {
        if (urlMapping.getStatus() == UrlStatus.DISABLED) {
            return UrlStatus.DISABLED;
        }
        if (DateTimeUtil.isExpired(urlMapping.getExpiresAt())) {
            return UrlStatus.EXPIRED;
        }
        if (urlMapping.getMaxClicks() != null
                && urlMapping.getClickCount() >= urlMapping.getMaxClicks()) {
            return UrlStatus.CLICK_LIMIT_REACHED;
        }
        return UrlStatus.ACTIVE;
    }
}