package com.tinyroute.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    
    public boolean isBlacklisted(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        try {
            String domain = extractDomain(url);
            return BLACKLISTED_DOMAINS.stream()
                    .anyMatch(domain::contains);
        } catch (Exception e) {
            log.warn("Could not parse domain from URL '{}', treating as blacklisted: {}", url, e.getMessage());
            return true; // fail closed
        }
    }

    private String extractDomain(String url) {
        return url.replaceAll("https?://", "")
                .split("/")[0]
                .toLowerCase();
    }
}