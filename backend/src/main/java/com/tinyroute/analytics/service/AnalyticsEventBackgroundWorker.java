package com.tinyroute.analytics.service;

import com.tinyroute.analytics.dto.ClickEventData;
import com.tinyroute.analytics.entity.ClickEvent;
import com.tinyroute.analytics.infra.GeoLocationService;
import com.tinyroute.analytics.infra.RedisAnalyticsEventQueue;
import com.tinyroute.analytics.infra.UserAgentParsingService;
import com.tinyroute.analytics.repository.ClickEventRepository;
import com.tinyroute.infra.network.ClientIpService;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.repository.UrlMappingRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsEventBackgroundWorker {

    private static final int MAX_BATCH_SIZE = 500;

    private final RedisAnalyticsEventQueue analyticsEventQueue;
    private final ClickEventRepository clickEventRepository;
    private final GeoLocationService geoLocationService;
    private final UserAgentParsingService userAgentParsingService;
    private final ClientIpService clientIpService;
    private final EntityManager entityManager;
    private final UniqueVisitorRegistrationService uniqueVisitorRegistrationService;
    private final RedisAnalyticsService redisAnalyticsService;
    private final UrlMappingRepository urlMappingRepository;

    @Scheduled(fixedDelayString = "5000", initialDelayString = "2000")
    @Transactional
    public void processQueuedClickEvents() {
        if (!analyticsEventQueue.hasEvents()) {
            return;
        }
        long queueSizeBefore = analyticsEventQueue.size();
        //log.info("Queue size before drain: {}", queueSizeBefore);

        List<ClickEventData> batch = analyticsEventQueue.drainBatch(MAX_BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }

        //log.info("Drained {} events from queue", batch.size());

        List<ClickEvent> persistedEvents = new ArrayList<>(batch.size());

        for (ClickEventData event : batch) {
            try {
                persistedEvents.add(enrichAndMapEvent(event));
            } catch (Exception e) {
                log.warn("Failed to enrich queued click event for urlId={}: {}",
                        event.getUrlMappingId(), e.getMessage(), e);
            }
        }

        if (persistedEvents.isEmpty()) {
            return;
        }

        int maxAttempts = 3;
        long backoffMs = 1000L;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {
                clickEventRepository.saveAll(persistedEvents);
                Map<Long, Integer> totalClicksByUrl = new HashMap<>();

                for (ClickEvent event : persistedEvents) {

                    Long urlId = event.getUrlMapping().getId();

                    totalClicksByUrl.merge(urlId, 1, Integer::sum);
                }

                for (Map.Entry<Long, Integer> entry : totalClicksByUrl.entrySet()) {
                    urlMappingRepository.incrementTotalClickCount(
                            entry.getKey(),
                            entry.getValue().longValue()
                    );
                }

                log.info("Persisted {} queued click events",
                        persistedEvents.size()
                );

                break;

            } catch (Exception e) {

                log.warn(
                        "Persist attempt {} failed for {} events: {}",
                        attempt,
                        persistedEvents.size(),
                        e.getMessage()
                );

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }

                backoffMs *= 2;
            }
        }
    }

    private ClickEvent enrichAndMapEvent(ClickEventData event) {
        LocalDateTime clickTime = event.getClickTime() != null
                ? event.getClickTime()
                : LocalDateTime.now();

        String ipHash = StringUtils.hasText(event.getIpHash())
                ? event.getIpHash()
                : clientIpService.hashIp(event.getIp());

        GeoLocationService.GeoLocation geo = geoLocationService.lookup(event.getIp());
        String country = geo != null ? geo.country() : "Unknown";
        String city = geo != null ? geo.city() : "Unknown";

        UserAgentParsingService.ParsedUserAgent parsed =
                userAgentParsingService.parse(event.getUserAgent());

        uniqueVisitorRegistrationService.registerIfFirstVisit(
                event.getUrlMappingId(),
                ipHash,
                clickTime
        );

        redisAnalyticsService.recordEnrichedAggregates(
                event.getUrlMappingId(),
                parsed.browser(),
                country,
                parsed.deviceType(),
                clickTime.toLocalDate()
        );

        ClickEvent clickEvent = new ClickEvent();
        clickEvent.setClickDate(clickTime);
        clickEvent.setUrlMapping(entityManager.getReference(UrlMapping.class, event.getUrlMappingId()));
        clickEvent.setIpHash(ipHash);
        clickEvent.setCountry(country);
        clickEvent.setCity(city);
        clickEvent.setBrowser(parsed.browser());
        clickEvent.setOs(parsed.os());
        clickEvent.setDeviceType(parsed.deviceType());

        clickEvent.setReferrer(StringUtils.hasText(event.getReferer())
                ? event.getReferer()
                : "Direct");

        clickEvent.setLanguage(StringUtils.hasText(event.getLanguage())
                ? event.getLanguage().split(",", 2)[0]
                : "Unknown");

        return clickEvent;
    }
}
