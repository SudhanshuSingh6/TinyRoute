package com.tinyroute.service.url;

import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.DuplicateAliasException;
import com.tinyroute.exception.InvalidUrlException;
import com.tinyroute.mapper.UrlMapper;
import com.tinyroute.repository.url.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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

    public UrlDetailsResponse createShortUrl(String originalUrl,
                                             String customAlias,
                                             LocalDateTime expiresAt,
                                             String title,
                                             User user) {
        LocalDateTime now = LocalDateTime.now();

        String normalizedOriginalUrl =
                urlValidationService.validateAndNormalizeDestinationUrl(originalUrl);

        boolean alreadyExists = urlMappingRepository.existsByOriginalUrlAndUser(normalizedOriginalUrl, user);
        if (alreadyExists) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "URL_ALREADY_EXISTS",
                    "A link for this destination already exists. Use the existing link instead."
            );
        }

        UrlMapping urlMapping = buildUrlMapping(
                normalizedOriginalUrl,
                expiresAt,
                title,
                user,
                now
        );

        if (customAlias != null && !customAlias.isBlank()) {
            return createWithCustomAlias(urlMapping, customAlias);
        }

        return createWithGeneratedAlias(urlMapping);
    }

    private UrlDetailsResponse createWithCustomAlias(UrlMapping urlMapping, String customAlias) {
        String alias = customAlias.trim();
        validateAlias(alias);

        if (urlMappingRepository.existsByShortUrl(alias)) {
            throw new DuplicateAliasException("Custom alias is already taken.");
        }

        urlMapping.setShortUrl(alias);

        try {
            UrlMapping saved = urlMappingRepository.save(urlMapping);
            return urlMapper.toUrlDetailsResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAliasException("Custom alias is already taken.");
        }
    }

    private UrlDetailsResponse createWithGeneratedAlias(UrlMapping urlMapping) {
        for (int attempt = 1; attempt <= MAX_ALIAS_ATTEMPTS; attempt++) {
            String candidate = generateShortCode();

            if (urlMappingRepository.existsByShortUrl(candidate)) {
                continue;
            }

            urlMapping.setShortUrl(candidate);

            try {
                UrlMapping saved = urlMappingRepository.save(urlMapping);
                return urlMapper.toUrlDetailsResponse(saved);
            } catch (DataIntegrityViolationException ex) {
                log.warn("Short URL DB collision on attempt {}: '{}'", attempt, candidate);
            }
        }

        throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "SHORT_URL_GENERATION_FAILED",
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
            return 10000;
        }
        if (user.getRole() == Role.ROLE_PREMIUM) {
            return 1000;
        }
        return 100;
    }

    private String generateShortCode() {
        return com.tinyroute.common.generator.SecureCodeGenerator
                .generateShortCode(SHORT_URL_LENGTH);
    }
}