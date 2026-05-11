package com.tinyroute.service.url;

import com.tinyroute.dto.url.response.UrlPreviewResponse;
import com.tinyroute.dto.user.response.PublicUrlDTO;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.DomainBlacklistedException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.exception.ErrorMessages;
import com.tinyroute.exception.InvalidDestinationUrlException;
import com.tinyroute.exception.UrlException;
import com.tinyroute.infra.qr.QrCodeService;
import com.tinyroute.mapper.UrlMapper;
import com.tinyroute.repository.url.UrlMappingRepository;
import com.tinyroute.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlLookupService {

    private final UrlMappingRepository urlMappingRepository;
    private final UserRepository userRepository;
    private final UrlMapper urlMapper;
    private final QrCodeService qrCodeService;
    private final UrlValidationService urlValidationService;

    public List<PublicUrlDTO> getPublicUrls(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCodes.USER_NOT_FOUND,
                        ErrorMessages.USER_NOT_FOUND
                ));

        return urlMappingRepository.findByUser(user).stream()
                .filter(this::isPubliclyAccessible)
                .map(urlMapper::toPublicBioLinkResponse)
                .toList();
    }

    public UrlPreviewResponse getPreview(String shortUrl) {
        UrlMapping urlMapping = getPublicUrlOrThrow(shortUrl);

        UrlPreviewResponse dto = new UrlPreviewResponse();
        final String originalUrl;

        try {
            originalUrl = urlValidationService.validateAndNormalizeDestinationUrl(urlMapping.getOriginalUrl());
        } catch (DomainBlacklistedException | InvalidDestinationUrlException ex) {
            log.warn("Blocked unsafe preview target for shortUrl '{}': {}", shortUrl, ex.getMessage());
            throw UrlException.notFound();
        }

        dto.setOriginalUrl(originalUrl);

        try {
            Document doc = Jsoup.connect(originalUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000)
                    .followRedirects(false)
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
        getPublicUrlOrThrow(shortUrl);
        String fullUrl = baseUrl + "/" + shortUrl;
        return qrCodeService.generatePng(fullUrl, 250, 250);
    }

    private UrlMapping getPublicUrlOrThrow(String shortUrl) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);

        if (!isPubliclyAccessible(urlMapping)) {
            throw UrlException.notFound();
        }

        return urlMapping;
    }

    private boolean isPubliclyAccessible(UrlMapping urlMapping) {
        if (urlMapping == null) {
            return false;
        }

        if (urlMapping.getStatus() != UrlStatus.ACTIVE) {
            return false;
        }

        if (urlMapping.getExpiresAt() != null && LocalDateTime.now().isAfter(urlMapping.getExpiresAt())) {
            return false;
        }

        return urlMapping.getMaxClicks() == null || urlMapping.getClickCount() < urlMapping.getMaxClicks();
    }
}