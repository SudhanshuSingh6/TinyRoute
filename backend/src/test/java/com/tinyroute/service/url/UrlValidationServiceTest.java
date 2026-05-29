package com.tinyroute.service.url;

import com.tinyroute.exception.DomainBlacklistedException;
import com.tinyroute.exception.InvalidDestinationUrlException;
import com.tinyroute.url.service.UrlValidationService;
import com.tinyroute.url.validation.DomainBlacklistValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlValidationServiceTest {

    @Mock
    private DomainBlacklistValidator domainBlacklistValidator;

    @InjectMocks
    private UrlValidationService urlValidationService;

    // ─── rejections that short-circuit before DNS ────────────────────────────────

    @Test
    void validate_nullUrl_throws() {
        assertThrows(InvalidDestinationUrlException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl(null));
    }

    @Test
    void validate_blankUrl_throws() {
        assertThrows(InvalidDestinationUrlException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl("   "));
    }

    @Test
    void validate_tooLongUrl_throws() {
        String longUrl = "http://example.com/" + "a".repeat(2050);
        assertThrows(InvalidDestinationUrlException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl(longUrl));
    }

    @Test
    void validate_nonHttpScheme_throws() {
        assertThrows(InvalidDestinationUrlException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl("ftp://example.com"));
    }

    @Test
    void validate_javascriptScheme_throws() {
        assertThrows(InvalidDestinationUrlException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl("javascript:alert(1)"));
    }

    @Test
    void validate_nonStandardPort_throws() {
        assertThrows(InvalidDestinationUrlException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl("http://example.com:9999"));
    }

    @Test
    void validate_blacklistedDomain_throws() {
        when(domainBlacklistValidator.isBlacklisted("malware.com")).thenReturn(true);
        assertThrows(DomainBlacklistedException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl("http://malware.com"));
    }

    // ─── SSRF guards (literal IPs / internal hostnames — no network) ──────────────

    @Test
    void validate_localhostHostname_throws() {
        assertThrows(DomainBlacklistedException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl("http://localhost"));
    }

    @Test
    void validate_loopbackIp_throws() {
        assertThrows(DomainBlacklistedException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl("http://127.0.0.1"));
    }

    @Test
    void validate_privateIp_throws() {
        assertThrows(DomainBlacklistedException.class,
                () -> urlValidationService.validateAndNormalizeDestinationUrl("http://192.168.1.1"));
    }

    // ─── accepted (literal public IP — resolves without DNS) ──────────────────────

    @Test
    void validate_publicIp_passes() {
        String result = urlValidationService.validateAndNormalizeDestinationUrl("http://8.8.8.8");
        assertTrue(result.contains("8.8.8.8"));
    }

    @Test
    void validate_publicIpStandardPort_passes() {
        String result = urlValidationService.validateAndNormalizeDestinationUrl("https://8.8.8.8:443");
        assertTrue(result.startsWith("https://"));
    }
}
