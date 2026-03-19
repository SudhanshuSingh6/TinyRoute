package com.tinyroute.config;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DomainBlacklistConfig {

    // known malicious / disallowed domains
    private static final Set<String> BLACKLISTED_DOMAINS = Set.of(
            "malware.com",            //random place holder examples
            "phishing.com",
            "spam.com",
            //"tinyroute.com",        // prevent same-domain infinite redirect loop
            "localhost"
    );

    public boolean isBlacklisted(String url) {
        try {
            String domain = extractDomain(url);
            return BLACKLISTED_DOMAINS.stream()
                    .anyMatch(domain::contains);
        } catch (Exception e) {
            return false;
        }
    }

    private String extractDomain(String url) {
        // strips http/https and path, returns just the domain
        return url.replaceAll("https?://", "")
                .split("/")[0]
                .toLowerCase();
    }
}