package com.tinyroute.service.url;

import com.tinyroute.dto.url.request.UpdateShortUrlRequest;
import com.tinyroute.dto.url.response.EditHistoryDTO;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.UrlEditHistory;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.InvalidUrlException;
import com.tinyroute.exception.LinkException;
import com.tinyroute.mapper.UrlMapper;
import com.tinyroute.repository.analytics.ClickEventRepository;
import com.tinyroute.repository.analytics.UrlUniqueVisitorRepository;
import com.tinyroute.repository.url.UrlEditHistoryRepository;
import com.tinyroute.repository.url.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UrlManagementService {

    private final UrlMappingRepository urlMappingRepository;
    private final UrlEditHistoryRepository urlEditHistoryRepository;
    private final ClickEventRepository clickEventRepository;
    private final UrlUniqueVisitorRepository urlUniqueVisitorRepository;
    private final UrlValidationService urlValidationService;
    private final UrlMapper urlMapper;

    // ---------- GET ALL USER URLS ----------

    public List<UrlDetailsResponse> getUrlsByUser(User user) {
        return urlMappingRepository.findByUser(user)
                .stream()
                .map(urlMapper::toUrlDetailsResponse)
                .toList();
    }

    // ---------- GET SINGLE URL ----------

    public UrlDetailsResponse getUrlDetails(String shortUrl, String username) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, username);
        return urlMapper.toUrlDetailsResponse(urlMapping);
    }

    // ---------- EDIT ORIGINAL URL + TITLE ----------

    @Transactional
    public UrlDetailsResponse editUrl(String shortUrl,
                                      UpdateShortUrlRequest request,
                                      User user) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, user);

        String normalizedOriginalUrl =
                urlValidationService.validateAndNormalizeDestinationUrl(request.getOriginalUrl());

        String normalizedTitle = normalizeTitle(request.getTitle());

        boolean originalUrlChanged = !Objects.equals(urlMapping.getOriginalUrl(), normalizedOriginalUrl);
        boolean titleChanged = !Objects.equals(urlMapping.getTitle(), normalizedTitle);

        if (!originalUrlChanged && !titleChanged) {
            return urlMapper.toUrlDetailsResponse(urlMapping);
        }

        if (originalUrlChanged) {
            boolean duplicateExists =
                    urlMappingRepository.existsByOriginalUrlAndUserAndIdNot(
                            normalizedOriginalUrl,
                            user,
                            urlMapping.getId()
                    );

            if (duplicateExists) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "URL_ALREADY_EXISTS",
                        "A link for this destination already exists. Use the existing link instead."
                );
            }

            UrlEditHistory history = new UrlEditHistory();
            history.setOldUrl(urlMapping.getOriginalUrl());
            history.setChangedAt(LocalDateTime.now());
            history.setUrlMapping(urlMapping);
            urlEditHistoryRepository.save(history);

            urlMapping.setOriginalUrl(normalizedOriginalUrl);
        }

        urlMapping.setTitle(normalizedTitle);

        return urlMapper.toUrlDetailsResponse(urlMappingRepository.save(urlMapping));
    }

    // ---------- UPDATE EXPIRY ----------

    @Transactional
    public UrlDetailsResponse updateExpiry(String shortUrl,
                                           LocalDateTime expiresAt,
                                           User user) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, user);

        LocalDateTime now = LocalDateTime.now();

        if (expiresAt != null && expiresAt.isBefore(now)) {
            throw new InvalidUrlException("Expiry cannot be in the past.");
        }

        if (Objects.equals(urlMapping.getExpiresAt(), expiresAt)) {
            return urlMapper.toUrlDetailsResponse(urlMapping);
        }

        urlMapping.setExpiresAt(expiresAt);

        if (urlMapping.getStatus() == UrlStatus.EXPIRED
                && (expiresAt == null || !now.isAfter(expiresAt))) {

            if (urlMapping.getMaxClicks() == null || urlMapping.getClickCount() < urlMapping.getMaxClicks()) {
                urlMapping.setStatus(UrlStatus.ACTIVE);
            } else {
                urlMapping.setStatus(UrlStatus.CLICK_LIMIT_REACHED);
            }
        }

        return urlMapper.toUrlDetailsResponse(urlMappingRepository.save(urlMapping));
    }

    // ---------- DISABLE ----------

    @Transactional
    public UrlDetailsResponse disableUrl(String shortUrl, String username) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, username);

        if (urlMapping.getStatus() != UrlStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "URL_DISABLE_INVALID",
                    "Only ACTIVE links can be disabled."
            );
        }

        urlMapping.setStatus(UrlStatus.DISABLED);
        return urlMapper.toUrlDetailsResponse(urlMappingRepository.save(urlMapping));
    }

    // ---------- ENABLE ----------

    @Transactional
    public UrlDetailsResponse enableUrl(String shortUrl, String username) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, username);

        if (urlMapping.getStatus() != UrlStatus.DISABLED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "URL_ENABLE_INVALID",
                    "Only DISABLED links can be enabled."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (urlMapping.getExpiresAt() != null && now.isAfter(urlMapping.getExpiresAt())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "URL_EXPIRED",
                    "Cannot enable an expired URL."
            );
        }

        if (urlMapping.getMaxClicks() != null && urlMapping.getClickCount() >= urlMapping.getMaxClicks()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CLICK_LIMIT_REACHED",
                    "Cannot enable a URL that has reached its click limit."
            );
        }

        urlMapping.setStatus(UrlStatus.ACTIVE);
        return urlMapper.toUrlDetailsResponse(urlMappingRepository.save(urlMapping));
    }

    // ---------- EDIT HISTORY ----------

    public List<EditHistoryDTO> getEditHistory(String shortUrl, String username) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, username);

        return urlEditHistoryRepository
                .findByUrlMappingOrderByChangedAtDesc(urlMapping)
                .stream()
                .map(urlMapper::toEditHistoryResponse)
                .toList();
    }

    // ---------- DELETE ----------

    @Transactional
    public void deleteUrl(String shortUrl, String username) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, username);

        // Delete child records first, then parent URL
        urlEditHistoryRepository.deleteByUrlMapping(urlMapping);
        urlUniqueVisitorRepository.deleteByUrlMapping(urlMapping);
        clickEventRepository.deleteByUrlMapping(urlMapping);
        urlMappingRepository.delete(urlMapping);
    }

    // ---------- HELPERS ----------

    private UrlMapping getOwnedUrlOrThrow(String shortUrl, String username) {
        return urlMappingRepository.findByShortUrlAndUserUsername(shortUrl, username)
                .orElseThrow(() -> LinkException.notFound("URL not found."));
    }

    private UrlMapping getOwnedUrlOrThrow(String shortUrl, User user) {
        if (user == null) {
            throw new InvalidUrlException("Authenticated user is required.");
        }

        return urlMappingRepository.findByShortUrlAndUserId(shortUrl, user.getId())
                .orElseThrow(() -> LinkException.notFound("URL not found."));
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }
}