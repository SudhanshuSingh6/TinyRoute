package com.tinyroute.url.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.tinyroute.exception.*;
import com.tinyroute.url.dto.UrlPreviewResponse;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.entity.UrlStatus;
import com.tinyroute.url.mapper.UrlMapper;
import com.tinyroute.url.repository.UrlMappingRepository;
import com.tinyroute.user.dto.PublicUrlDTO;
import com.tinyroute.user.entity.User;
import com.tinyroute.user.mapper.UserMapper;
import com.tinyroute.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class UrlPreviewService {

    private final UrlMappingRepository urlMappingRepository;
    private final UserRepository userRepository;
    private final UrlMapper urlMapper;
    private final UserMapper userMapper;
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
                .map(userMapper::toPublicBioLinkResponse)
                .toList();
    }

    public UrlPreviewResponse getPreview(String shortUrl) {
        UrlMapping urlMapping = getPublicUrlOrThrow(shortUrl);

        UrlPreviewResponse dto = new UrlPreviewResponse();

        final String originalUrl;

        try {
            originalUrl = urlValidationService.validateAndNormalizeDestinationUrl(urlMapping.getOriginalUrl());

        } catch (DomainBlacklistedException | InvalidDestinationUrlException ex) {

            log.warn(
                    "Blocked unsafe preview target for shortUrl '{}': {}",
                    shortUrl,
                    ex.getMessage()
            );

            throw UrlException.notFound();
        }

        dto.setOriginalUrl(originalUrl);

        try {

            Document doc = Jsoup.connect(originalUrl)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/124.0.0.0 Safari/537.36"
                    )
                    .header(
                            "Accept-Language",
                            "en-US,en;q=0.9"
                    )
                    .header(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
                    )
                    .referrer("https://www.google.com")
                    .timeout(10000)
                    .followRedirects(true)
                    .get();

            String ogTitle = doc.select("meta[property=og:title]").attr("content");

            dto.setTitle(!ogTitle.isBlank()
                    ? ogTitle
                    : doc.title()
            );

            String description = doc.select(
                    "meta[property=og:description]"
            ).attr("content");

            if (description.isBlank()) {
                description = doc.select("meta[name=description]")
                        .attr("content");
            }

            dto.setDescription(!description.isBlank()
                    ? description : null
            );

            String image = doc.select(
                    "meta[property=og:image]"
            ).attr("content");

            dto.setImageUrl(
                    !image.isBlank()
                            ? image
                            : null
            );
            if (dto.getTitle() == null || dto.getTitle().isBlank()) {
                dto.setTitle(originalUrl);
            }
            if (dto.getImageUrl() == null || dto.getImageUrl().isBlank()) {
                dto.setImageUrl(
                        "https://placehold.co/600x300?text=No+Preview"
                );
            }
            URI uri = URI.create(originalUrl);

            if (dto.getTitle() == null || dto.getTitle().isBlank()) {
                dto.setTitle(uri.getHost());
            }

        } catch (HttpStatusException e) {

            log.warn(
                    "Site returned {} for '{}'",
                    e.getStatusCode(),
                    originalUrl
            );

        } catch (UnsupportedMimeTypeException e) {

            log.warn(
                    "Not an HTML page: '{}'",
                    originalUrl
            );

        } catch (SocketTimeoutException e) {

            log.warn(
                    "Timeout fetching preview for '{}'",
                    originalUrl
            );

        } catch (IOException e) {

            log.warn(
                    "Network error fetching '{}': {}",
                    originalUrl,
                    e.getMessage()
            );

        } catch (Exception e) {

            log.error(
                    "Unexpected error in getPreview for '{}': {}",
                    originalUrl,
                    e.getMessage(),
                    e
            );
        }

        return dto;
    }

    public byte[] generateQr(String shortUrl, String baseUrl) {
        getPublicUrlOrThrow(shortUrl);
        String fullUrl = baseUrl + "/" + shortUrl;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(fullUrl, BarcodeFormat.QR_CODE, 250, 250);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
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

        return urlMapping.getClickCount() < urlMapping.getMaxClicks();
    }
}