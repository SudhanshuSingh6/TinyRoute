package com.tinyroute.common.generator;

import com.tinyroute.exception.ShortCodeExhaustedException;
import com.tinyroute.repository.url.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class SecureCodeGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 5;

    private final UrlMappingRepository urlMappingRepository;

    private String generateUniqueShortCode() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String code = generateShortCode(DEFAULT_LENGTH);
            if (!urlMappingRepository.existsByShortUrl(code)) {
                return code;
            }
        }
        throw new ShortCodeExhaustedException(
                "Failed to generate a unique short code after " + MAX_ATTEMPTS + " attempts"
        );
    }

    public static String generateShortCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(SECURE_RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}