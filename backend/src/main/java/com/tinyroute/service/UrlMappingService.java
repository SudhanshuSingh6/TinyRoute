package com.tinyroute.service;

import com.tinyroute.config.DomainBlacklistConfig;
import com.tinyroute.dtos.ClickEventDTO;
import com.tinyroute.dtos.UrlMappingDTO;
import com.tinyroute.models.ClickEvent;
import com.tinyroute.models.UrlMapping;
import com.tinyroute.models.UrlStatus;
import com.tinyroute.models.User;
import com.tinyroute.repository.ClickEventRepository;
import com.tinyroute.repository.UrlMappingRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

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
    private DomainBlacklistConfig domainBlacklistConfig;    // NEW

    public UrlMappingDTO createShortUrl(String originalUrl, String customAlias,
                                        LocalDateTime expiresAt, Integer maxClicks,
                                        String title, boolean isPublic, User user) {

        // domain blacklist check
        if (domainBlacklistConfig.isBlacklisted(originalUrl)) {
            throw new RuntimeException("This domain is not allowed.");
        }

        // duplicate URL check — return existing if same user already shortened it
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
                throw new RuntimeException("Alias '" + customAlias + "' is already taken. Please choose another.");
            }
            urlMapping.setShortUrl(customAlias);
            urlMapping.setCustomAlias(customAlias);
        } else {
            urlMapping.setShortUrl(generateShortUrl());
        }

        UrlMapping savedUrlMapping = urlMappingRepository.save(urlMapping);
        return convertToDto(savedUrlMapping);
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

    // called by RedirectController — returns UrlMapping with updated status
    public UrlMapping getOriginalUrl(String shortUrl) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) return null;

        // check and update status before returning
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

        // valid click — increment count, update lastClickedAt, save click event
        urlMapping.setClickCount(urlMapping.getClickCount() + 1);
        urlMapping.setLastClickedAt(LocalDateTime.now());        // NEW
        urlMappingRepository.save(urlMapping);

        ClickEvent clickEvent = new ClickEvent();
        clickEvent.setClickDate(LocalDateTime.now());
        clickEvent.setUrlMapping(urlMapping);
        clickEventRepository.save(clickEvent);

        return urlMapping;
    }
}