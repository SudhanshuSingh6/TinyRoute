package com.tinyroute.service.url;

import com.tinyroute.common.generator.SecureCodeGenerator;
import com.tinyroute.dto.url.request.CreateShortUrlRequest;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.*;
import com.tinyroute.mapper.UrlMapper;
import com.tinyroute.repository.url.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCreationService {

    private static final int SHORT_URL_LENGTH = 8;
    private static final int MAX_ALIAS_ATTEMPTS = 10;

    private static final Set<String> RESERVED_ALIASES = Set.of(
            "api",
            "admin",
            "login",
            "register",
            "swagger",
            "docs",
            "health",
            "preview",
            "qr"
    );

    private final UrlMappingRepository urlMappingRepository;
    private final UrlValidationService urlValidationService;
    private final UrlMapper urlMapper;

    public UrlDetailsResponse createShortUrl(User user,CreateShortUrlRequest request) {
        LocalDateTime now = LocalDateTime.now();

        String normalizedOriginalUrl =
                urlValidationService.validateAndNormalizeDestinationUrl(request.getOriginalUrl());

        boolean alreadyExists =
                urlMappingRepository.existsByOriginalUrlAndUser(normalizedOriginalUrl, user);

        if (alreadyExists) {
            throw AlreadyExistsException.custom(ErrorCodes.URL_ALREADY_EXISTS,
                    "A URL for this destination already exists. Use the existing URL instead.");
        }

        UrlMapping urlMapping = buildUrlMapping(
                normalizedOriginalUrl,
                request.getExpiresAt(),
                request.getTitle(),
                user,
                now
        );
        String customAlias = request.getCustomAlias().trim();

        if (!customAlias.isBlank()) {
            return createWithCustomAlias(urlMapping, customAlias);
        }

        return createWithGeneratedAlias(urlMapping);
    }

    private UrlDetailsResponse createWithCustomAlias(UrlMapping urlMapping, String customAlias) {
        String alias = customAlias.trim();
        validateAlias(alias);

        if (urlMappingRepository.existsByShortUrl(alias)) {
            throw AlreadyExistsException.alias();
        }

        urlMapping.setShortUrl(alias);

        try {
            UrlMapping saved = urlMappingRepository.save(urlMapping);
            return urlMapper.toUrlDetailsResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw AlreadyExistsException.alias();
        }
    }

    private UrlDetailsResponse createWithGeneratedAlias(UrlMapping urlMapping) {
        for (int attempt = 1; attempt <= MAX_ALIAS_ATTEMPTS; attempt++) {
            String candidate = SecureCodeGenerator.generateShortCode(SHORT_URL_LENGTH);
            urlMapping.setShortUrl(candidate);

            try {
                UrlMapping saved = urlMappingRepository.save(urlMapping);
                return urlMapper.toUrlDetailsResponse(saved);
            } catch (DataIntegrityViolationException ex) {
                log.warn("Short URL DB collision on attempt {}: '{}'", attempt, candidate);
            }
        }
        throw new ShortUrlGenerationFailedException(
                "Could not generate a unique short URL after " + MAX_ALIAS_ATTEMPTS + " attempts."
        );
    }

    private UrlMapping buildUrlMapping(String normalizedOriginalUrl,
                                       LocalDateTime expiresAt,
                                       String title,
                                       User user,
                                       LocalDateTime now) {
        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setOriginalUrl(normalizedOriginalUrl);
        urlMapping.setUser(user);
        urlMapping.setCreatedDate(now);
        urlMapping.setExpiresAt(expiresAt);
        urlMapping.setMaxClicks(determineMaxClicks(user));
        urlMapping.setTitle(normalizeTitle(title));
        urlMapping.setStatus(UrlStatus.ACTIVE);
        urlMapping.setClickCount(0);
        urlMapping.setLastClickedAt(null);
        return urlMapping;
    }

    private void validateAlias(String alias) {
        if (RESERVED_ALIASES.contains(alias.toLowerCase())) {
            throw new InvalidUrlException("Alias is reserved and cannot be used.");
        }
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }

        String trimmed = title.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Integer determineMaxClicks(User user) {
        if (user.getRole() == Role.ROLE_ADMIN) {
            return 100000;
        }
        if (user.getRole() == Role.ROLE_PREMIUM) {
            return 10000;
        }
        return 1000;
    }
}