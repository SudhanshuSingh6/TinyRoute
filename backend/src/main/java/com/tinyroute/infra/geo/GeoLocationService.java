package com.tinyroute.infra.geo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeoLocationService {

    private static final GeoLocation UNKNOWN = new GeoLocation("Unknown", "Unknown");
    private static final GeoLocation LOCAL = new GeoLocation("Local", "Local");

    private final RestTemplate restTemplate;

    @SuppressWarnings("unchecked")
    public GeoLocation lookup(String ip) {
        if (ip == null || ip.isBlank()) {
            return UNKNOWN;
        }
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return LOCAL;
        }

        try {
            Map<String, Object> response = restTemplate.getForObject(
                    "http://ip-api.com/json/" + ip + "?fields=country,city,status",
                    Map.class
            );

            if (response != null && "success".equals(response.get("status"))) {
                String country = (String) response.getOrDefault("country", "Unknown");
                String city = (String) response.getOrDefault("city", "Unknown");
                return new GeoLocation(country, city);
            }
            return UNKNOWN;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    public record GeoLocation(String country, String city) { }
}
