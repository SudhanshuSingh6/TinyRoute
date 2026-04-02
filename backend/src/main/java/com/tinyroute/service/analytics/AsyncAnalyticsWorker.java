package com.tinyroute.service.analytics;

import com.tinyroute.common.hash.HashUtil;
import com.tinyroute.entity.ClickEvent;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.infra.geo.GeoLocationService;
import com.tinyroute.infra.ua.UserAgentParsingService;
import com.tinyroute.repository.analytics.ClickEventRepository;
import com.tinyroute.repository.url.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAnalyticsWorker {

    private final ClickEventRepository clickEventRepository;
    private final GeoLocationService geoLocationService;
    private final UserAgentParsingService userAgentParsingService;

    @Async
    public void recordClickEvent(UrlMapping urlMapping,
                                 String ip,
                                 String userAgentHeader,
                                 String referrerHeader,
                                 String languageHeader,
                                 LocalDateTime clickDate) {
        try {
            ClickEvent clickEvent = new ClickEvent();
            clickEvent.setClickDate(clickDate);
            clickEvent.setUrlMapping(urlMapping);

            String ipHash = HashUtil.sha256Hex(ip);
            clickEvent.setIpHash(ipHash);

            // Geo (safe)
            GeoLocationService.GeoLocation geo = geoLocationService.lookup(ip);
            if (geo != null) {
                clickEvent.setCountry(geo.country());
                clickEvent.setCity(geo.city());
            } else {
                clickEvent.setCountry("Unknown");
                clickEvent.setCity("Unknown");
            }

            // UA (safe)
            UserAgentParsingService.ParsedUserAgent parsedUserAgent =
                    userAgentParsingService.parse(userAgentHeader);

            if (parsedUserAgent != null) {
                clickEvent.setBrowser(parsedUserAgent.browser());
                clickEvent.setOs(parsedUserAgent.os());
                clickEvent.setDeviceType(parsedUserAgent.deviceType());
            } else {
                clickEvent.setBrowser("Unknown");
                clickEvent.setOs("Unknown");
                clickEvent.setDeviceType("Unknown");
            }

            // Referrer
            clickEvent.setReferrer(referrerHeader != null ? referrerHeader : "Direct");

            // Language
            clickEvent.setLanguage(languageHeader != null ? languageHeader.split(",")[0] : "Unknown");

            clickEventRepository.save(clickEvent);

        } catch (Exception e) {
            log.warn("Failed to record async click event for urlMappingId {}: {}", urlMapping, e.getMessage());
        }
    }
}
