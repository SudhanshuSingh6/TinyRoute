package com.tinyroute.url.service;

import com.tinyroute.analytics.repository.ClickEventRepository;
import com.tinyroute.analytics.repository.UrlUniqueVisitorRepository;
import com.tinyroute.exception.*;
import com.tinyroute.infra.cache.RedirectCacheService;
import com.tinyroute.url.dto.EditHistoryResponse;
import com.tinyroute.url.dto.UpdateShortUrlRequest;
import com.tinyroute.url.dto.UrlDetailsResponse;
import com.tinyroute.url.entity.UrlEditHistory;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.entity.UrlStatus;
import com.tinyroute.url.mapper.UrlMapper;
import com.tinyroute.url.repository.UrlEditHistoryRepository;
import com.tinyroute.url.repository.UrlMappingRepository;
import com.tinyroute.user.entity.User;
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
    private final RedirectCacheService redirectCacheService;


    public List<UrlDetailsResponse> getUrlsByUser(User user) {
        return urlMappingRepository.findByUser(user)
                .stream()
                .map(urlMapper::toUrlDetailsResponse)
                .toList();
    }

    public UrlDetailsResponse getUrlDetails(String shortUrl, Long userId) {
        return urlMapper.toUrlDetailsResponse(getOwnedUrlOrThrow(shortUrl, userId));
    }

    public List<EditHistoryResponse> getEditHistory(String shortUrl, Long userId) {
        return urlEditHistoryRepository
                .findByUrlMappingOrderByChangedAtDesc(getOwnedUrlOrThrow(shortUrl, userId))
                .stream()
                .map(urlMapper::toEditHistoryResponse)
                .toList();
    }

    @Transactional
    public UrlDetailsResponse editUrl(String shortUrl,
                                      UpdateShortUrlRequest request,
                                      Long userId) {

        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, userId);

        String normalizedOriginalUrl =
                urlValidationService.validateAndNormalizeDestinationUrl(request.getOriginalUrl());
        String normalizedTitle = normalizeTitle(request.getTitle());

        boolean originalChanged = !Objects.equals(urlMapping.getOriginalUrl(), normalizedOriginalUrl);
        boolean titleChanged = !Objects.equals(urlMapping.getTitle(), normalizedTitle);

        if (!originalChanged && !titleChanged) {
            return urlMapper.toUrlDetailsResponse(urlMapping);
        }

        if (originalChanged) {
            boolean duplicateExists =
                    urlMappingRepository.existsByOriginalUrlAndUserAndIdNot(
                            normalizedOriginalUrl, urlMapping.getUser(), urlMapping.getId());

            if (duplicateExists) {
                throw new ApiException(HttpStatus.CONFLICT,
                        ErrorCodes.URL_ALREADY_EXISTS, ErrorMessages.URL_ALREADY_EXISTS);
            }

            UrlEditHistory history = new UrlEditHistory();
            history.setOldUrl(urlMapping.getOriginalUrl());
            history.setChangedAt(LocalDateTime.now());
            history.setUrlMapping(urlMapping);
            urlEditHistoryRepository.save(history);

            urlMapping.setOriginalUrl(normalizedOriginalUrl);
        }

        urlMapping.setTitle(normalizedTitle);
        UrlDetailsResponse response = urlMapper.toUrlDetailsResponse(
                urlMappingRepository.save(urlMapping));

        redirectCacheService.evict(shortUrl);
        return response;
    }

    @Transactional
    public UrlDetailsResponse updateExpiry(String shortUrl,
                                           LocalDateTime expiresAt,
                                           Long userId) {

        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, userId);
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

            if (urlMapping.getClickCount() < urlMapping.getMaxClicks()) {
                urlMapping.setStatus(UrlStatus.ACTIVE);
            } else {
                urlMapping.setStatus(UrlStatus.CLICK_LIMIT_REACHED);
            }
        }

        UrlDetailsResponse response = urlMapper.toUrlDetailsResponse(
                urlMappingRepository.save(urlMapping));

        redirectCacheService.evict(shortUrl);
        return response;
    }

    @Transactional
    public UrlDetailsResponse disableUrl(String shortUrl, Long userId) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, userId);

        if (urlMapping.getStatus() != UrlStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCodes.URL_DISABLE_INVALID, ErrorMessages.URL_DISABLE_INVALID);
        }

        urlMapping.setStatus(UrlStatus.DISABLED);
        UrlDetailsResponse response = urlMapper.toUrlDetailsResponse(
                urlMappingRepository.save(urlMapping));

        redirectCacheService.evict(shortUrl);
        return response;
    }

    @Transactional
    public UrlDetailsResponse enableUrl(String shortUrl, Long userId) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, userId);
        LocalDateTime now = LocalDateTime.now();

        if (urlMapping.getStatus() != UrlStatus.DISABLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCodes.URL_ENABLE_INVALID, ErrorMessages.URL_ENABLE_INVALID);
        }

        if (urlMapping.getExpiresAt() != null && now.isAfter(urlMapping.getExpiresAt())) {
            throw UrlException.expired();
        }

        if (urlMapping.getClickCount() >= urlMapping.getMaxClicks()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCodes.CLICK_LIMIT_REACHED, ErrorMessages.CLICK_LIMIT_REACHED);
        }

        urlMapping.setStatus(UrlStatus.ACTIVE);
        UrlDetailsResponse response = urlMapper.toUrlDetailsResponse(
                urlMappingRepository.save(urlMapping));

        redirectCacheService.evict(shortUrl);
        return response;
    }

    @Transactional
    public void deleteUrl(String shortUrl, Long userId) {
        UrlMapping urlMapping = getOwnedUrlOrThrow(shortUrl, userId);

        urlEditHistoryRepository.deleteByUrlMapping(urlMapping);
        urlUniqueVisitorRepository.deleteByUrlMapping(urlMapping);
        clickEventRepository.deleteByUrlMapping(urlMapping);
        urlMappingRepository.delete(urlMapping);

        redirectCacheService.evict(shortUrl);
    }


    private UrlMapping getOwnedUrlOrThrow(String shortUrl, Long userId) {
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    ErrorCodes.AUTHENTICATION_FAILED, ErrorMessages.AUTHENTICATION_FAILED);
        }

        return urlMappingRepository.findByShortUrlAndUserId(shortUrl, userId)
                .orElseThrow(UrlException::notFound);
    }

    private String normalizeTitle(String title) {
        if (title == null) return null;
        String trimmed = title.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}