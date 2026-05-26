package com.tinyroute.redirect.service;

import com.tinyroute.analytics.dto.ClickEventData;
import com.tinyroute.analytics.infra.ClickEventDataBuilder;
import com.tinyroute.analytics.service.RedisAnalyticsService;
import com.tinyroute.infra.cache.RedirectCacheEntry;
import com.tinyroute.infra.cache.RedirectCacheService;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.entity.UrlStatus;
import com.tinyroute.url.repository.UrlMappingRepository;
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
    private final ClientIpService clientIpService;
    private final RedirectCacheService redirectCacheService;
    private final RedisAnalyticsService redisAnalyticsService;
    private final ClickEventDataBuilder clickEventDataBuilder;

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
        UrlStatus resolvedStatus = resolveStatus(urlMapping,now);

        if (urlMapping.getStatus() != resolvedStatus) {
            urlMappingRepository.updateStatus(urlMapping.getId(), resolvedStatus);
            urlMapping.setStatus(resolvedStatus);
            redirectCacheService.evict(shortUrl);
        }

        if (resolvedStatus != UrlStatus.ACTIVE) {
            return urlMapping;
        }

        String ip = clientIpService.resolveClientIp(request);
        String ipHash = clientIpService.hashIp(ip);

        ClickEventData clickEvent = clickEventDataBuilder.buildFromRequest(
                urlMapping.getId(),
                request,
                ip,
                ipHash,
                request.getHeader("User-Agent"),
                now
        );

        redisAnalyticsService.recordClick(clickEvent);

        return urlMapping;
    }


    private UrlStatus resolveStatus(UrlMapping urlMapping,LocalDateTime now) {
        if (urlMapping.getStatus() == UrlStatus.DISABLED) {
            return UrlStatus.DISABLED;
        }
        if (now != null && now.isAfter(LocalDateTime.now()))
            return UrlStatus.EXPIRED;
        if (urlMapping.getMaxClicks() != null) {
            long dbClicks = urlMapping.getClickCount();
            long redisClicks = 0L;
            try {
                redisClicks = redisAnalyticsService.getUniqueVisitorCount(urlMapping.getId(),now.toLocalDate());
            } catch (Exception e) {
                log.warn("Failed to read Redis clicks for urlId={}", urlMapping.getId(), e);
            }
            if (dbClicks + redisClicks >= urlMapping.getMaxClicks()) {
                return UrlStatus.CLICK_LIMIT_REACHED;
            }
        }
        return UrlStatus.ACTIVE;
    }
}
