package com.tinyroute.analytics.service;

import com.tinyroute.analytics.entity.ClickEvent;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.infra.geo.GeoLocationService;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.infra.ua.UserAgentParsingService;
import com.tinyroute.analytics.repository.ClickEventRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
//marked for delete
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAnalyticsWorker {

    private final ClickEventRepository      clickEventRepository;
    private final GeoLocationService        geoLocationService;
    private final UserAgentParsingService   userAgentParsingService;
    private final ClientIpService           clientIpService;
    private final EntityManager             entityManager;

    @Async
    @Transactional
    public void recordClickEvent(Long urlMappingId,
                                 String ip,
                                 String userAgentHeader,
                                 String referrerHeader,
                                 String languageHeader,
                                 LocalDateTime clickDate) {
        try {
            ClickEvent clickEvent = new ClickEvent();
            clickEvent.setClickDate(clickDate);

            // getReference() returns a proxy — no SELECT issued, FK constraint satisfied
            clickEvent.setUrlMapping(entityManager.getReference(UrlMapping.class, urlMappingId));

            String ipHash = clientIpService.hashIp(ip);
            clickEvent.setIpHash(ipHash);

            // Geo (safe — timeouts configured in AppConfig RestTemplate)
            GeoLocationService.GeoLocation geo = geoLocationService.lookup(ip);
            if (geo != null) {
                clickEvent.setCountry(geo.country());
                clickEvent.setCity(geo.city());
            } else {
                clickEvent.setCountry("Unknown");
                clickEvent.setCity("Unknown");
            }

            // UA parsing (safe — never throws)
            UserAgentParsingService.ParsedUserAgent parsedUa =
                    userAgentParsingService.parse(userAgentHeader);
            if (parsedUa != null) {
                clickEvent.setBrowser(parsedUa.browser());
                clickEvent.setOs(parsedUa.os());
                clickEvent.setDeviceType(parsedUa.deviceType());
            } else {
                clickEvent.setBrowser("Unknown");
                clickEvent.setOs("Unknown");
                clickEvent.setDeviceType("Unknown");
            }

            clickEvent.setReferrer(referrerHeader != null ? referrerHeader : "Direct");
            clickEvent.setLanguage(
                    languageHeader != null ? languageHeader.split(",")[0] : "Unknown");

            clickEventRepository.save(clickEvent);

        } catch (Exception e) {
            log.warn("Failed to record async click event for urlMappingId={}: {}",
                    urlMappingId, e.getMessage());
        }
    }
}