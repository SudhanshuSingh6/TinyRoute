package com.tinyroute.analytics.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoLocationService {

    private static final GeoLocation UNKNOWN = new GeoLocation("Unknown", "Unknown");
    private static final GeoLocation LOCAL = new GeoLocation("Local", "Local");

    private final RestTemplate restTemplate;

    public GeoLocation lookup(String ip) {

        if (ip == null || ip.isBlank()) {
            return UNKNOWN;
        }

        if ("127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)) {
            return LOCAL;
        }

        try {
            IpApiResponse response =
                    restTemplate.getForObject(
                            "https://ip-api.com/json/"
                                    + ip
                                    + "?fields=country,city,status",
                            IpApiResponse.class
                    );

            if (response != null
                    && "success".equals(response.status())) {

                return new GeoLocation(
                        response.country() != null
                                ? response.country()
                                : "Unknown",

                        response.city() != null
                                ? response.city()
                                : "Unknown"
                );
            }

        } catch (Exception e) {
            log.debug("Geo lookup failed for ip={}", ip, e);
        }

        return UNKNOWN;
    }

    private record IpApiResponse(
            String status,
            String country,
            String city
    ) {}

    public record GeoLocation(
            String country,
            String city
    ) {}
}