package com.tinyroute.service.redirect;

import com.tinyroute.common.time.DateTimeUtil;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlRedirectService {

    private final UrlMappingRepository urlMappingRepository;
    private final UniqueVisitorRegistrationService uniqueVisitorRegistrationService;
    private final AsyncAnalyticsWorker asyncAnalyticsWorker;
    private final ClientIpService clientIpService;

    @Transactional
    public UrlMapping getOriginalUrl(String shortUrl, HttpServletRequest request) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        UrlStatus resolvedStatus = resolveStatus(urlMapping);

        if (urlMapping.getStatus() != resolvedStatus) {
            urlMappingRepository.updateStatus(urlMapping.getId(), resolvedStatus);
            urlMapping.setStatus(resolvedStatus);
        }

        if (resolvedStatus != UrlStatus.ACTIVE) {
            return urlMapping;
        }

        String ip = clientIpService.resolveClientIp(request);
        String ipHash = clientIpService.hashIp(ip);

        boolean isFirstVisit = uniqueVisitorRegistrationService.registerIfFirstVisit(
                urlMapping.getId(), ipHash, now
        );

        // Always update last clicked
        urlMappingRepository.updateLastClickedAt(urlMapping.getId(), now);
        urlMapping.setLastClickedAt(now);

        // Only unique visitor increments clickCount
        if (isFirstVisit) {
            urlMappingRepository.incrementClickCount(urlMapping.getId());
            urlMapping.setClickCount(urlMapping.getClickCount() + 1);
        }

        asyncAnalyticsWorker.recordClickEvent(
                urlMapping,
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