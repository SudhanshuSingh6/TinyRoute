package com.tinyroute.common.url;

import com.tinyroute.exception.InvalidDestinationUrlException;

import java.net.IDN;
import java.util.Locale;

public class DomainNormalizer {
    public static String normalize(String host) {
        if (host == null || host.isBlank()) {
            throw new InvalidDestinationUrlException("originalUrl host is invalid.");
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);

        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }

        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        // IPv6 literals contain ':' and should not be IDN-converted
        if (normalized.contains(":")) {
            return normalized;
        }

        try {
            return IDN.toASCII(normalized);
        } catch (Exception ex) {
            throw new InvalidDestinationUrlException("originalUrl host is invalid.");
        }
    }
}
