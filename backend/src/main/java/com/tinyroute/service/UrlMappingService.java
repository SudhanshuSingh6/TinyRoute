package com.tinyroute.service;

import com.tinyroute.dtos.*;
import com.tinyroute.models.ClickEvent;
import com.tinyroute.models.UrlEditHistory;
import com.tinyroute.models.UrlMapping;
import com.tinyroute.models.UrlStatus;
import com.tinyroute.models.User;
import com.tinyroute.repository.ClickEventRepository;
import com.tinyroute.repository.UrlEditHistoryRepository;
import com.tinyroute.repository.UrlMappingRepository;
import com.tinyroute.config.DomainBlacklistConfig;
import com.tinyroute.repository.UserRepository;
import jakarta.persistence.LockModeType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.UnsupportedMimeTypeException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import ua_parser.Client;
import ua_parser.Parser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class UrlMappingService {

    private static final int  SHORT_URL_LENGTH   = 8;
    private static final int  MAX_ALIAS_ATTEMPTS = 10;
    private static final String CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Parser UA_PARSER;
    static {
        try {
            UA_PARSER = new Parser();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(
                    "Failed to initialise ua-parser: " + e.getMessage());
        }
    }

    private UrlMappingRepository urlMappingRepository;
    private ClickEventRepository clickEventRepository;
    private UrlEditHistoryRepository urlEditHistoryRepository;
    private UserRepository userRepository;
    private UserService userService;
    private DomainBlacklistConfig domainBlacklistConfig;

    public UrlMappingDTO createShortUrl(String originalUrl, String customAlias,
                                        LocalDateTime expiresAt, Integer maxClicks,
                                        String title, boolean isPublic, User user) {
        if (domainBlacklistConfig.isBlacklisted(originalUrl)) {
            throw new RuntimeException("This domain is not allowed.");
        }

        UrlMapping existing = urlMappingRepository.findByOriginalUrlAndUser(originalUrl, user);
        if (existing != null && existing.getStatus() == UrlStatus.ACTIVE && !existing.isDeleted()) {
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
            urlMapping.setShortUrl(generateUniqueShortUrl());
        }
        return convertToDto(urlMappingRepository.save(urlMapping));
    }

    public void deleteUrl(Long id, String username) {
        UrlMapping urlMapping = urlMappingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("URL not found with id: " + id));

        if (!urlMapping.getUser().getUsername().equals(username)) {
            throw new RuntimeException("FORBIDDEN");
        }

        urlMapping.setDeleted(true);
        urlMapping.setDeletedAt(LocalDateTime.now());
        urlMappingRepository.save(urlMapping);
    }

    public UrlMappingDTO editUrl(Long id, String newOriginalUrl, String username) {
        UrlMapping urlMapping = urlMappingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("URL not found with id: " + id));

        if (!urlMapping.getUser().getUsername().equals(username)) {
            throw new RuntimeException("FORBIDDEN");
        }

        if (domainBlacklistConfig.isBlacklisted(newOriginalUrl)) {
            throw new RuntimeException("This domain is not allowed.");
        }

        UrlEditHistory history = new UrlEditHistory();
        history.setOldUrl(urlMapping.getOriginalUrl());
        history.setChangedAt(LocalDateTime.now());
        history.setUrlMapping(urlMapping);
        urlEditHistoryRepository.save(history);

        urlMapping.setOriginalUrl(newOriginalUrl);
        return convertToDto(urlMappingRepository.save(urlMapping));
    }

    public List<UrlEditHistoryDTO> getEditHistory(Long id, String username) {
        UrlMapping urlMapping = urlMappingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("URL not found with id: " + id));

        if (!urlMapping.getUser().getUsername().equals(username)) {
            throw new RuntimeException("FORBIDDEN");
        }

        return urlEditHistoryRepository
                .findByUrlMappingOrderByChangedAtDesc(urlMapping)
                .stream()
                .map(h -> {
                    UrlEditHistoryDTO dto = new UrlEditHistoryDTO();
                    dto.setId(h.getId());
                    dto.setOldUrl(h.getOldUrl());
                    dto.setChangedAt(h.getChangedAt());
                    return dto;
                })
                .toList();
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

    private String generateUniqueShortUrl() {
        for (int attempt = 1; attempt <= MAX_ALIAS_ATTEMPTS; attempt++) {
            String candidate = generateShortCode();
            if (urlMappingRepository.findByShortUrl(candidate) == null) {
                return candidate;
            }
            log.warn("Short URL collision on attempt {}: '{}'", attempt, candidate);
        }
        throw new RuntimeException(
                "Could not generate a unique short URL after " + MAX_ALIAS_ATTEMPTS + " attempts.");
    }

    private String generateShortCode() {
        StringBuilder sb = new StringBuilder(SHORT_URL_LENGTH);
        for (int i = 0; i < SHORT_URL_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(SECURE_RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    public List<UrlMappingDTO> getUrlsByUser(User user) {
        return urlMappingRepository.findByUser(user).stream()
                .filter(u -> !u.isDeleted())
                .map(this::convertToDto)
                .toList();
    }

    public AnalyticsDTO getAnalytics(String shortUrl, LocalDateTime start, LocalDateTime end) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) return null;

        List<ClickEvent> clicks = clickEventRepository
                .findByUrlMappingAndClickDateBetween(urlMapping, start, end);

        AnalyticsDTO dto = new AnalyticsDTO();
        dto.setTotalClicks(clicks.size());
        dto.setUniqueClicks(clicks.stream().filter(ClickEvent::isUniqueClick).count());

        dto.setClicksByCountry(clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCountry() != null ? c.getCountry() : "Unknown",
                        Collectors.counting())));

        dto.setClicksByDevice(clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getDeviceType() != null ? c.getDeviceType() : "Unknown",
                        Collectors.counting())));

        dto.setClicksByBrowser(clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getBrowser() != null ? c.getBrowser() : "Unknown",
                        Collectors.counting())));

        dto.setClicksByOs(clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getOs() != null ? c.getOs() : "Unknown",
                        Collectors.counting())));

        dto.setClicksByReferrer(clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getReferrer() != null ? c.getReferrer() : "Direct",
                        Collectors.counting())));

        dto.setClicksByHour(clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getClickDate().getHour(),
                        Collectors.counting())));

        dto.setClicksByDayOfWeek(clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getClickDate().getDayOfWeek().toString(),
                        Collectors.counting())));

        int peakHour = dto.getClicksByHour().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);
        dto.setPeakHour(peakHour);

        LocalDateTime now = LocalDateTime.now();
        long last24h = clicks.stream()
                .filter(c -> c.getClickDate().isAfter(now.minusHours(24)))
                .count();
        long previous24h = clicks.stream()
                .filter(c -> c.getClickDate().isAfter(now.minusHours(48))
                        && c.getClickDate().isBefore(now.minusHours(24)))
                .count();
        String trend = last24h > previous24h ? "UP"
                : last24h < previous24h ? "DOWN"
                : "STABLE";
        dto.setClickVelocity(new ClickVelocityDTO(last24h, previous24h, trend));

        return dto;
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

    public BioPageDTO getBioPage(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return null;

        userService.incrementBioPageViews(username);
        long updatedViewCount = user.getBioPageViews() + 1;

        UserProfileDTO profile = new UserProfileDTO();
        profile.setUsername(user.getUsername());
        profile.setBio(user.getBio());
        profile.setAvatarUrl(user.getAvatarUrl());
        profile.setBioPageViews(updatedViewCount);

        List<UrlMappingDTO> publicUrls = urlMappingRepository.findByUser(user).stream()
                .filter(u -> u.isPublic()
                        && u.getStatus() == UrlStatus.ACTIVE
                        && !u.isDeleted())
                .map(this::convertToDto)
                .toList();

        BioPageDTO dto = new BioPageDTO();
        dto.setProfile(profile);
        dto.setUrls(publicUrls);
        return dto;
    }

    public UrlPreviewDTO getPreview(String shortUrl) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null || urlMapping.isDeleted()) return null;

        UrlPreviewDTO dto = new UrlPreviewDTO();
        String originalUrl = urlMapping.getOriginalUrl();
        dto.setOriginalUrl(originalUrl);

        try {
            Document doc = Jsoup.connect(originalUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000)
                    .get();

            String ogTitle = doc.select("meta[property=og:title]").attr("content");
            dto.setTitle(!ogTitle.isBlank() ? ogTitle : doc.title());

            String desc = doc.select("meta[name=description]").attr("content");
            dto.setDescription(!desc.isBlank() ? desc : null);

            String image = doc.select("meta[property=og:image]").attr("abs:content");
            dto.setImageUrl(!image.isBlank() ? image : null);

        } catch (HttpStatusException e) {
            log.warn("Site returned {} for '{}'", e.getStatusCode(), originalUrl);
        } catch (UnsupportedMimeTypeException e) {
            log.warn("Not an HTML page: '{}'", originalUrl);
        } catch (SocketTimeoutException e) {
            log.warn("Timeout fetching preview for '{}'", originalUrl);
        } catch (IOException e) {
            log.warn("Network error fetching '{}': {}", originalUrl, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in getPreview for '{}': {}", originalUrl, e.getMessage(), e);
        }
        return dto;
    }

    public byte[] generateQr(String shortUrl, String baseUrl) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null || urlMapping.isDeleted()) return null;

        try {
            String fullUrl = baseUrl + "/" + shortUrl;
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(fullUrl, BarcodeFormat.QR_CODE, 250, 250);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    @Transactional
    public UrlMapping getOriginalUrl(String shortUrl, HttpServletRequest request) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) return null;
        if (urlMapping.isDeleted()) return null;

        if (urlMapping.getStatus() == UrlStatus.DISABLED) {
            return urlMapping; // no click recorded, no status change
        }

        if (urlMapping.getExpiresAt() != null &&
                LocalDateTime.now().isAfter(urlMapping.getExpiresAt())) {
            urlMapping.setStatus(UrlStatus.EXPIRED);
            urlMappingRepository.save(urlMapping);
            return urlMapping; // no click recorded
        }

        if (urlMapping.getMaxClicks() != null &&
                urlMapping.getClickCount() >= urlMapping.getMaxClicks()) {
            urlMapping.setStatus(UrlStatus.CLICK_LIMIT_REACHED);
            urlMappingRepository.save(urlMapping);
            return urlMapping; // no click recorded
        }

        urlMapping.setClickCount(urlMapping.getClickCount() + 1);
        urlMapping.setLastClickedAt(LocalDateTime.now());
        urlMappingRepository.save(urlMapping);

        ClickEvent clickEvent = new ClickEvent();
        clickEvent.setClickDate(LocalDateTime.now());
        clickEvent.setUrlMapping(urlMapping);

        String ip     = getClientIp(request);
        String ipHash = hashIp(ip);
        clickEvent.setIpHash(ipHash);

        boolean isUnique = !clickEventRepository.existsByUrlMappingAndIpHash(urlMapping, ipHash);
        clickEvent.setUniqueClick(isUnique);

        populateGeo(clickEvent, ip);
        populateDevice(clickEvent, request.getHeader("User-Agent"));

        String referrer = request.getHeader("Referer");
        clickEvent.setReferrer(referrer != null ? referrer : "Direct");

        String language = request.getHeader("Accept-Language");
        clickEvent.setLanguage(language != null ? language.split(",")[0] : "Unknown");

        clickEventRepository.save(clickEvent);
        return urlMapping;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

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

    @SuppressWarnings("unchecked")
    private void populateGeo(ClickEvent clickEvent, String ip) {
        try {
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

    private void populateDevice(ClickEvent clickEvent, String userAgentString) {
        try {
            if (userAgentString == null || userAgentString.isBlank()) {
                clickEvent.setBrowser("Unknown");
                clickEvent.setOs("Unknown");
                clickEvent.setDeviceType("Unknown");
                return;
            }
            Client client = UA_PARSER.parse(userAgentString);
            clickEvent.setBrowser(client.userAgent.family);
            clickEvent.setOs(client.os.family);
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