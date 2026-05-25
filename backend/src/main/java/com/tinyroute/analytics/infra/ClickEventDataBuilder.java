package com.tinyroute.analytics.infra;

import com.tinyroute.analytics.dto.ClickEventData;
import com.tinyroute.infra.network.ClientIpService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Builder for ClickEventData from HTTP request
 * Extracts raw metadata (no enrichment here)
 */
@Component
@RequiredArgsConstructor
public class ClickEventDataBuilder {

    private final ClientIpService clientIpService;

    /**
     * Build ClickEventData from HTTP request
     * Called in redirect thread (fast path)
     */
    public ClickEventData buildFromRequest(
            Long urlMappingId,
            HttpServletRequest request,
            String userAgent) {

        String ip = clientIpService.resolveClientIp(request);
        String ipHash = clientIpService.hashIp(ip);

        return buildFromRequest(urlMappingId, request, ip, ipHash, userAgent, LocalDateTime.now());
    }

    public ClickEventData buildFromRequest(
            Long urlMappingId,
            HttpServletRequest request,
            String ip,
            String ipHash,
            String userAgent,
            LocalDateTime clickTime) {

        return ClickEventData.builder()
                .urlMappingId(urlMappingId)
                .ip(ip)
                .ipHash(ipHash)
                .userAgent(userAgent != null ? userAgent : "Unknown")
                .referer(request.getHeader("Referer"))
                .language(request.getHeader("Accept-Language"))
                .clickTime(clickTime)
                .enriched(false)
                .build();
    }
}
