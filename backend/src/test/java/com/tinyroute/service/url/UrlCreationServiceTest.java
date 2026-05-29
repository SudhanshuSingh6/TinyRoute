package com.tinyroute.service.url;

import com.tinyroute.config.RoleLimitConfig;
import com.tinyroute.exception.AlreadyExistsException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.exception.InvalidUrlException;
import com.tinyroute.url.dto.CreateShortUrlRequest;
import com.tinyroute.url.dto.UrlDetailsResponse;
import com.tinyroute.user.entity.Role;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.url.entity.UrlStatus;
import com.tinyroute.user.entity.User;
import com.tinyroute.url.mapper.UrlMapper;
import com.tinyroute.url.repository.UrlMappingRepository;
import com.tinyroute.url.service.UrlCreationService;
import com.tinyroute.url.service.UrlValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlCreationServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private UrlValidationService urlValidationService;
    @Mock
    private UrlMapper urlMapper;
    @Mock
    private RoleLimitConfig roleLimits;

    @InjectMocks
    private UrlCreationService urlCreationService;

    // ─── helpers ────────────────────────────────────────────────────────────────

    private User userWithRole(Role role) {
        User user = new User();
        user.setId(1L);
        user.setUsername("sudhanshu");
        user.setEmail("sudhanshu@example.com");
        user.setRole(role);
        return user;
    }

    private CreateShortUrlRequest request(String url) {
        CreateShortUrlRequest req = new CreateShortUrlRequest();
        req.setOriginalUrl(url);
        return req;
    }

    private void stubValidation(String rawUrl, String normalized) {
        when(urlValidationService.validateAndNormalizeDestinationUrl(rawUrl)).thenReturn(normalized);
    }

    private void stubSaveReturnsArg() {
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubMapper() {
        when(urlMapper.toUrlDetailsResponse(any(UrlMapping.class))).thenAnswer(inv -> {
            UrlMapping m = inv.getArgument(0);
            UrlDetailsResponse dto = new UrlDetailsResponse();
            dto.setShortUrl(m.getShortUrl());
            dto.setStatus(m.getStatus());
            dto.setClickCount(m.getClickCount());
            dto.setTitle(m.getTitle());
            dto.setExpiresAt(m.getExpiresAt());
            return dto;
        });
    }

    private UrlMapping captureSaved() {
        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(urlMappingRepository).save(captor.capture());
        return captor.getValue();
    }

    // ─── tests ──────────────────────────────────────────────────────────────────

    @Test
    void createShortUrl_generatesAlias_whenNoCustomAlias() {
        User user = userWithRole(Role.ROLE_USER);
        stubValidation("https://example.com", "https://example.com");
        when(urlMappingRepository.existsByOriginalUrlAndUser("https://example.com", user)).thenReturn(false);
        when(roleLimits.getUser()).thenReturn(10000);
        stubSaveReturnsArg();
        stubMapper();

        UrlDetailsResponse res = urlCreationService.createShortUrl(user, request("https://example.com"));

        assertNotNull(res);
        UrlMapping saved = captureSaved();
        assertEquals(8, saved.getShortUrl().length());
        assertEquals(UrlStatus.ACTIVE, saved.getStatus());
        verify(urlValidationService).validateAndNormalizeDestinationUrl("https://example.com");
    }

    @Test
    void createShortUrl_usesCustomAlias_whenProvided() {
        User user = userWithRole(Role.ROLE_USER);
        CreateShortUrlRequest req = request("https://example.com");
        req.setCustomAlias("launch");

        stubValidation("https://example.com", "https://example.com");
        when(urlMappingRepository.existsByOriginalUrlAndUser("https://example.com", user)).thenReturn(false);
        when(roleLimits.getUser()).thenReturn(10000);
        when(urlMappingRepository.existsByShortUrl("launch")).thenReturn(false);
        stubSaveReturnsArg();
        stubMapper();

        urlCreationService.createShortUrl(user, req);

        assertEquals("launch", captureSaved().getShortUrl());
    }

    @Test
    void createShortUrl_throwsConflict_whenCustomAliasTaken() {
        User user = userWithRole(Role.ROLE_USER);
        CreateShortUrlRequest req = request("https://example.com");
        req.setCustomAlias("launch");

        stubValidation("https://example.com", "https://example.com");
        when(urlMappingRepository.existsByOriginalUrlAndUser("https://example.com", user)).thenReturn(false);
        when(roleLimits.getUser()).thenReturn(10000);
        when(urlMappingRepository.existsByShortUrl("launch")).thenReturn(true);

        AlreadyExistsException ex = assertThrows(AlreadyExistsException.class,
                () -> urlCreationService.createShortUrl(user, req));
        assertEquals(ErrorCodes.DUPLICATE_ALIAS, ex.getErrorCode());
        verify(urlMappingRepository, never()).save(any());
    }

    @Test
    void createShortUrl_throwsConflict_whenDuplicateOriginalUrl() {
        User user = userWithRole(Role.ROLE_USER);
        stubValidation("https://example.com", "https://example.com");
        when(urlMappingRepository.existsByOriginalUrlAndUser("https://example.com", user)).thenReturn(true);

        AlreadyExistsException ex = assertThrows(AlreadyExistsException.class,
                () -> urlCreationService.createShortUrl(user, request("https://example.com")));
        assertEquals(ErrorCodes.URL_ALREADY_EXISTS, ex.getErrorCode());
        verify(urlMappingRepository, never()).save(any());
    }

    @Test
    void createShortUrl_blocksReservedAlias() {
        User user = userWithRole(Role.ROLE_USER);
        CreateShortUrlRequest req = request("https://example.com");
        req.setCustomAlias("api");

        stubValidation("https://example.com", "https://example.com");
        when(urlMappingRepository.existsByOriginalUrlAndUser("https://example.com", user)).thenReturn(false);
        when(roleLimits.getUser()).thenReturn(10000);

        assertThrows(InvalidUrlException.class,
                () -> urlCreationService.createShortUrl(user, req));
        verify(urlMappingRepository, never()).save(any());
    }

    @Test
    void createShortUrl_appliesMaxClicksFromUserPlan() {
        User user = userWithRole(Role.ROLE_USER);
        stubValidation("https://example.com", "https://example.com");
        when(urlMappingRepository.existsByOriginalUrlAndUser("https://example.com", user)).thenReturn(false);
        when(roleLimits.getUser()).thenReturn(10000);
        stubSaveReturnsArg();
        stubMapper();

        urlCreationService.createShortUrl(user, request("https://example.com"));

        assertEquals(10000, captureSaved().getMaxClicks());
    }

    @Test
    void createShortUrl_appliesMaxClicksFromPremiumPlan() {
        User user = userWithRole(Role.ROLE_PREMIUM);
        stubValidation("https://example.com", "https://example.com");
        when(urlMappingRepository.existsByOriginalUrlAndUser("https://example.com", user)).thenReturn(false);
        when(roleLimits.getPremium()).thenReturn(100000);
        stubSaveReturnsArg();
        stubMapper();

        urlCreationService.createShortUrl(user, request("https://example.com"));

        assertEquals(100000, captureSaved().getMaxClicks());
    }

    @Test
    void createShortUrl_setsExpiresAt_whenProvided() {
        User user = userWithRole(Role.ROLE_USER);
        LocalDateTime expiry = LocalDateTime.now().plusDays(5);
        CreateShortUrlRequest req = request("https://example.com");
        req.setExpiresAt(expiry);

        stubValidation("https://example.com", "https://example.com");
        when(urlMappingRepository.existsByOriginalUrlAndUser("https://example.com", user)).thenReturn(false);
        when(roleLimits.getUser()).thenReturn(10000);
        stubSaveReturnsArg();
        stubMapper();

        urlCreationService.createShortUrl(user, req);

        assertEquals(expiry, captureSaved().getExpiresAt());
    }
}
