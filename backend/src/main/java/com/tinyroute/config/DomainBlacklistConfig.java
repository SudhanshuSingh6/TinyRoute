package com.tinyroute.config;

import com.tinyroute.common.url.DomainNormalizer;
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
            "localhost",
            "localhost.localdomain",
            "local",
            "internal",
            "metadata.google.internal",
            "metadata",
            "169.254.169.254",
            "169.254.170.2"
    );

    public boolean isBlacklisted(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }

        try {
            String domain = DomainNormalizer.normalize(host);

            return BLACKLISTED_DOMAINS.stream()
                    .map(DomainNormalizer::normalize)
                    .anyMatch(blocked -> domain.equals(blocked) || domain.endsWith("." + blocked));
        } catch (Exception e) {
            log.warn("Could not normalize domain '{}', treating as blacklisted: {}", host, e.getMessage());
            return true;
        }
    }

}
