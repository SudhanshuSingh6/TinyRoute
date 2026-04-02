package com.tinyroute.service.redirect;

import com.tinyroute.common.hash.HashUtil;
import com.tinyroute.common.network.IpUtil;
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

    //@Transactional
    public UrlMapping getOriginalUrl(String shortUrl, HttpServletRequest request) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        if (urlMapping.getStatus() == UrlStatus.DISABLED) {
            return urlMapping;
        }

        if (urlMapping.getExpiresAt() != null && now.isAfter(urlMapping.getExpiresAt())) {
            if (urlMapping.getStatus() != UrlStatus.EXPIRED) {
                urlMappingRepository.updateStatus(urlMapping.getId(), UrlStatus.EXPIRED);
                urlMapping.setStatus(UrlStatus.EXPIRED);
            }
            return urlMapping;
        }

        if (urlMapping.getMaxClicks() != null &&
                urlMapping.getClickCount() >= urlMapping.getMaxClicks()) {
            if (urlMapping.getStatus() != UrlStatus.CLICK_LIMIT_REACHED) {
                urlMappingRepository.updateStatus(urlMapping.getId(), UrlStatus.CLICK_LIMIT_REACHED);
                urlMapping.setStatus(UrlStatus.CLICK_LIMIT_REACHED);
            }
            return urlMapping;
        }

        String ip = IpUtil.resolveClientIp(request);
        String ipHash = HashUtil.sha256Hex(ip);

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

            // If unique click just reached limit, mark for future requests
            if (urlMapping.getMaxClicks() != null &&
                    urlMapping.getClickCount() >= urlMapping.getMaxClicks()) {
                urlMappingRepository.updateStatus(urlMapping.getId(), UrlStatus.CLICK_LIMIT_REACHED);
                urlMapping.setStatus(UrlStatus.CLICK_LIMIT_REACHED);
            }
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
}