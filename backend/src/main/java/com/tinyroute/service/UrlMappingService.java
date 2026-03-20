package com.tinyroute.service;

import com.tinyroute.dtos.ClickEventDTO;
import com.tinyroute.dtos.UrlMappingDTO;
import com.tinyroute.models.ClickEvent;
import com.tinyroute.models.UrlMapping;
import com.tinyroute.models.UrlStatus;
import com.tinyroute.models.User;
import com.tinyroute.repository.ClickEventRepository;
import com.tinyroute.repository.UrlMappingRepository;
import com.tinyroute.config.DomainBlacklistConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ua_parser.Client;
import ua_parser.Parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class UrlMappingService {

    private UrlMappingRepository urlMappingRepository;
    private ClickEventRepository clickEventRepository;
    private DomainBlacklistConfig domainBlacklistConfig;

    public UrlMappingDTO createShortUrl(String originalUrl, String customAlias,
                                        LocalDateTime expiresAt, Integer maxClicks,
                                        String title, boolean isPublic, User user) {
        if (domainBlacklistConfig.isBlacklisted(originalUrl)) {
            throw new RuntimeException("This domain is not allowed.");
        }

        UrlMapping existing = urlMappingRepository.findByOriginalUrlAndUser(originalUrl, user);
        if (existing != null && existing.getStatus() == UrlStatus.ACTIVE) {
            return convertToDto(existing);
        }

        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setOriginalUrl(originalUrl);
        urlMapping.setUser(user);
        urlMapping.setCreatedDate(LocalDateTime.now());
        urlMapping.setExpiresAt(expiresAt);
        urlMapping.setMaxClicks(maxClicks);
        urlMapping.setTitle(title);
        urlMapping.setPublic(isPublic);
        urlMapping.setStatus(UrlStatus.ACTIVE);

        if (customAlias != null && !customAlias.isBlank()) {
            if (urlMappingRepository.findByShortUrl(customAlias) != null) {
                throw new RuntimeException("Alias '" + customAlias + "' is already taken.");
            }
            urlMapping.setShortUrl(customAlias);
            urlMapping.setCustomAlias(customAlias);
        } else {
            urlMapping.setShortUrl(generateShortUrl());
        }

        return convertToDto(urlMappingRepository.save(urlMapping));
    }

    private UrlMappingDTO convertToDto(UrlMapping urlMapping) {
        UrlMappingDTO dto = new UrlMappingDTO();
        dto.setId(urlMapping.getId());
        dto.setOriginalUrl(urlMapping.getOriginalUrl());
        dto.setShortUrl(urlMapping.getShortUrl());
        dto.setCustomAlias(urlMapping.getCustomAlias());
        dto.setTitle(urlMapping.getTitle());
        dto.setClickCount(urlMapping.getClickCount());
        dto.setCreatedDate(urlMapping.getCreatedDate());
        dto.setExpiresAt(urlMapping.getExpiresAt());
        dto.setLastClickedAt(urlMapping.getLastClickedAt());
        dto.setMaxClicks(urlMapping.getMaxClicks());
        dto.setPublic(urlMapping.isPublic());
        dto.setStatus(urlMapping.getStatus());
        dto.setUsername(urlMapping.getUser().getUsername());
        return dto;
    }

    private String generateShortUrl() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder shortUrl = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            shortUrl.append(characters.charAt(random.nextInt(characters.length())));
        }
        return shortUrl.toString();
    }

    public List<UrlMappingDTO> getUrlsByUser(User user) {
        return urlMappingRepository.findByUser(user).stream()
                .map(this::convertToDto)
                .toList();
    }

    public List<ClickEventDTO> getClickEventsByDate(String shortUrl, LocalDateTime start, LocalDateTime end) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping != null) {
            return clickEventRepository.findByUrlMappingAndClickDateBetween(urlMapping, start, end)
                    .stream()
                    .collect(Collectors.groupingBy(
                            click -> click.getClickDate().toLocalDate(),
                            Collectors.counting()))
                    .entrySet().stream()
                    .map(entry -> {
                        ClickEventDTO dto = new ClickEventDTO();
                        dto.setClickDate(entry.getKey());
                        dto.setCount(entry.getValue());
                        return dto;
                    })
                    .collect(Collectors.toList());
        }
        return null;
    }

    public Map<LocalDate, Long> getTotalClicksByUserAndDate(User user, LocalDate start, LocalDate end) {
        List<UrlMapping> urlMappings = urlMappingRepository.findByUser(user);
        List<ClickEvent> clickEvents = clickEventRepository
                .findByUrlMappingInAndClickDateBetween(
                        urlMappings,
                        start.atStartOfDay(),
                        end.plusDays(1).atStartOfDay());
        return clickEvents.stream()
                .collect(Collectors.groupingBy(
                        click -> click.getClickDate().toLocalDate(),
                        Collectors.counting()));
    }

    // called by RedirectController — now takes HttpServletRequest for analytics
    public UrlMapping getOriginalUrl(String shortUrl, HttpServletRequest request) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) return null;

        // status checks
        if (urlMapping.getExpiresAt() != null &&
                LocalDateTime.now().isAfter(urlMapping.getExpiresAt())) {
            urlMapping.setStatus(UrlStatus.EXPIRED);
            urlMappingRepository.save(urlMapping);
            return urlMapping;
        }

        if (urlMapping.getMaxClicks() != null &&
                urlMapping.getClickCount() >= urlMapping.getMaxClicks()) {
            urlMapping.setStatus(UrlStatus.CLICK_LIMIT_REACHED);
            urlMappingRepository.save(urlMapping);
            return urlMapping;
        }

        // increment click count and last clicked
        urlMapping.setClickCount(urlMapping.getClickCount() + 1);
        urlMapping.setLastClickedAt(LocalDateTime.now());
        urlMappingRepository.save(urlMapping);

        // build and save click event with full analytics
        ClickEvent clickEvent = new ClickEvent();
        clickEvent.setClickDate(LocalDateTime.now());
        clickEvent.setUrlMapping(urlMapping);

        // 1. get real IP — check X-Forwarded-For first (proxy/load balancer)
        String ip = getClientIp(request);

        // 2. hash IP for privacy
        String ipHash = hashIp(ip);
        clickEvent.setIpHash(ipHash);

        // 3. unique click check
        boolean isUnique = !clickEventRepository.existsByUrlMappingAndIpHash(urlMapping, ipHash);
        clickEvent.setUniqueClick(isUnique);

        // 4. geo from ip-api.com
        populateGeo(clickEvent, ip);

        // 5. device/browser/OS from User-Agent
        populateDevice(clickEvent, request.getHeader("User-Agent"));

        // 6. referrer
        String referrer = request.getHeader("Referer");
        clickEvent.setReferrer(referrer != null ? referrer : "Direct");

        // 7. language
        String language = request.getHeader("Accept-Language");
        clickEvent.setLanguage(language != null ? language.split(",")[0] : "Unknown");

        clickEventRepository.save(clickEvent);
        return urlMapping;
    }

    // reads X-Forwarded-For first — essential when behind a proxy/load balancer
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // first IP in chain is the real client
        }
        return request.getRemoteAddr();
    }

    // SHA-256 hash — one-way, privacy safe, never store raw IP
    private String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // calls ip-api.com — free, no API key needed, returns JSON
    @SuppressWarnings("unchecked")
    private void populateGeo(ClickEvent clickEvent, String ip) {
        try {
            // skip for localhost — ip-api returns error for 127.0.0.1
            if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1")) {
                clickEvent.setCountry("Local");
                clickEvent.setCity("Local");
                return;
            }
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> response = restTemplate.getForObject(
                    "http://ip-api.com/json/" + ip + "?fields=country,city,status",
                    Map.class
            );
            if (response != null && "success".equals(response.get("status"))) {
                clickEvent.setCountry((String) response.getOrDefault("country", "Unknown"));
                clickEvent.setCity((String) response.getOrDefault("city", "Unknown"));
            } else {
                clickEvent.setCountry("Unknown");
                clickEvent.setCity("Unknown");
            }
        } catch (Exception e) {
            clickEvent.setCountry("Unknown");
            clickEvent.setCity("Unknown");
        }
    }

    // uap-java parses User-Agent into browser, OS, device
    private void populateDevice(ClickEvent clickEvent, String userAgentString) {
        try {
            if (userAgentString == null || userAgentString.isBlank()) {
                clickEvent.setBrowser("Unknown");
                clickEvent.setOs("Unknown");
                clickEvent.setDeviceType("Unknown");
                return;
            }
            Parser parser = new Parser();
            Client client = parser.parse(userAgentString);
            clickEvent.setBrowser(client.userAgent.family);
            clickEvent.setOs(client.os.family);
            // uap-java device family: "Spider" = bot, "Other" = desktop, else = mobile/tablet
            String device = client.device.family;
            if (device.equalsIgnoreCase("Other")) {
                clickEvent.setDeviceType("Desktop");
            } else if (device.equalsIgnoreCase("Spider")) {
                clickEvent.setDeviceType("Bot");
            } else {
                clickEvent.setDeviceType("Mobile");
            }
        } catch (Exception e) {
            clickEvent.setBrowser("Unknown");
            clickEvent.setOs("Unknown");
            clickEvent.setDeviceType("Unknown");
        }
    }
}