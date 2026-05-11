package com.tinyroute.service.url;

import com.tinyroute.common.url.DomainNormalizer;
import com.tinyroute.config.DomainBlacklistConfig;
import com.tinyroute.exception.DomainBlacklistedException;
import com.tinyroute.exception.ErrorMessages;
import com.tinyroute.exception.InvalidDestinationUrlException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UrlValidationService {

    private static final Set<String> ALLOWED_URL_SCHEMES = Set.of("http", "https");

    private final DomainBlacklistConfig domainBlacklistConfig;

    public String validateAndNormalizeDestinationUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidDestinationUrlException(ErrorMessages.INVALID_DESTINATION_URL);
        }

        if (rawUrl.length() > 2048) {
            throw new InvalidDestinationUrlException("URL too long.");
        }

        final URI parsedUri;
        try {
            parsedUri = new URI(rawUrl.trim()).normalize();
        } catch (URISyntaxException ex) {
            throw new InvalidDestinationUrlException(ErrorMessages.INVALID_DESTINATION_URL);
        }

        String scheme = parsedUri.getScheme();
        String host = parsedUri.getHost();

        if (scheme == null || host == null || host.isBlank()) {
            throw new InvalidDestinationUrlException(ErrorMessages.INVALID_DESTINATION_URL);
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);

        if (!ALLOWED_URL_SCHEMES.contains(normalizedScheme)) {
            throw new InvalidDestinationUrlException("Only http and https URLs are allowed.");
        }

        // restrict ports
        int port = parsedUri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new InvalidDestinationUrlException("Only standard ports are allowed.");
        }

        String normalizedHost = DomainNormalizer.normalize(host);

        if (domainBlacklistConfig.isBlacklisted(normalizedHost) || isInternalHostname(normalizedHost)) {
            throw new DomainBlacklistedException();
        }

        ensurePublicAddress(normalizedHost);

        try {
            URI normalizedUri = new URI(
                    normalizedScheme,
                    parsedUri.getUserInfo(),
                    normalizedHost,
                    parsedUri.getPort(),
                    parsedUri.getRawPath(),
                    parsedUri.getRawQuery(),
                    parsedUri.getRawFragment()
            ).normalize();

            return normalizedUri.toString();

        } catch (URISyntaxException ex) {
            throw new InvalidDestinationUrlException(ErrorMessages.INVALID_DESTINATION_URL);
        }
    }

    private boolean isInternalHostname(String host) {
        return "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".localdomain");
    }

    private void ensurePublicAddress(String host) {
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new InvalidDestinationUrlException(ErrorMessages.INVALID_DESTINATION_URL);
        }

        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new DomainBlacklistedException("This domain is not allowed.");
            }
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet4Address ipv4) {
            return isBlockedIpv4(ipv4);
        }

        if (address instanceof Inet6Address ipv6) {
            return isBlockedIpv6(ipv6);
        }

        return false;
    }

    private boolean isBlockedIpv4(Inet4Address ipv4) {
        byte[] octets = ipv4.getAddress();
        int b0 = octets[0] & 0xFF;
        int b1 = octets[1] & 0xFF;

        // 100.64.0.0/10 (Carrier-grade NAT)
        if (b0 == 100 && b1 >= 64 && b1 <= 127) {
            return true;
        }

        // 198.18.0.0/15 (Network benchmark tests)
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return true;
        }

        // 0.0.0.0/8 and 240.0.0.0/4 (special-use ranges)
        if (b0 == 0 || b0 >= 240) {
            return true;
        }

        return false;
    }

    private boolean isBlockedIpv6(Inet6Address ipv6) {
        byte[] octets = ipv6.getAddress();
        int first = octets[0] & 0xFF;

        // fc00::/7 (Unique local addresses)
        return (first & 0xFE) == 0xFC;
    }
}
