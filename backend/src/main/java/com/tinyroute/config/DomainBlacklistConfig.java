package com.tinyroute.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class DomainBlacklistConfig {

    private static final Set<String> BLACKLISTED_DOMAINS = Set.of(
            "malware.com",
            "phishing.com",
            "spam.com",
            "localhost"
    );

    public boolean isBlacklisted(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }

        try {
            String domain = normalizeDomain(host);

            return BLACKLISTED_DOMAINS.stream()
                    .map(this::normalizeDomain)
                    .anyMatch(blocked -> domain.equals(blocked) || domain.endsWith("." + blocked));
        } catch (Exception e) {
            log.warn("Could not normalize domain '{}', treating as blacklisted: {}", host, e.getMessage());
            return true;
        }
    }

    private String normalizeDomain(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);

        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }

        return normalized;
    }
}