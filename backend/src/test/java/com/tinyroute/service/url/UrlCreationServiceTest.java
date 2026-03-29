package com.tinyroute.service.url;

import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.DuplicateAliasException;
import com.tinyroute.exception.InvalidUrlException;
import com.tinyroute.mapper.UrlMapper;
import com.tinyroute.repository.analytics.ClickEventRepository;
import com.tinyroute.repository.analytics.UrlUniqueVisitorRepository;
import com.tinyroute.repository.url.UrlEditHistoryRepository;
import com.tinyroute.repository.url.UrlMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlCreationServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private ClickEventRepository clickEventRepository;
    @Mock
    private UrlEditHistoryRepository urlEditHistoryRepository;
    @Mock
    private UrlUniqueVisitorRepository urlUniqueVisitorRepository;
    @Mock
    private UrlValidationService urlValidationService;
    @Mock
    private UrlMapper urlMapper;

    @InjectMocks
    private UrlCreationService urlCreationService;

    private void stubUrlMapperToDetailsResponse() {
        when(urlMapper.toUrlDetailsResponse(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            UrlDetailsResponse dto = new UrlDetailsResponse();
            dto.setShortUrl(mapping.getShortUrl());
            dto.setStatus(mapping.getStatus());
            dto.setClickCount(mapping.getClickCount());
            dto.setTitle(mapping.getTitle());
            dto.setMaxClicks(mapping.getMaxClicks());
            dto.setExpiresAt(mapping.getExpiresAt());
            return dto;
        });
    }

    @Test
    void createShortUrl_whenActiveLinkSameDestination_throwsConflict() {
        User user = testUser();
        String normalized = "https://example.com/page";
        when(urlValidationService.validateAndNormalizeDestinationUrl("https://example.com/page")).thenReturn(normalized);


        when(urlMappingRepository.existsByOriginalUrlAndUser(
                normalized,
                user
        )).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> urlCreationService.createShortUrl("https://example.com/page", null, null, null, user));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals("URL_ALREADY_EXISTS", ex.getErrorCode());
    }

    @Test
    void createShortUrl_whenCustomAliasTaken_throwsDuplicateAlias() {
        User user = testUser();
        String normalized = "https://uniq.com";

        when(urlValidationService.validateAndNormalizeDestinationUrl("https://uniq.com"))
                .thenReturn(normalized);

        when(urlMappingRepository.existsByOriginalUrlAndUser(normalized, user))
                .thenReturn(false);

        when(urlMappingRepository.existsByShortUrl("taken-alias"))
                .thenReturn(true);

        assertThrows(DuplicateAliasException.class,
                () -> urlCreationService.createShortUrl(
                        "https://uniq.com",
                        "taken-alias",
                        null,
                        null,
                        user
                ));

        verify(urlMappingRepository, never()).save(any(UrlMapping.class));
    }

    private User testUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("sudhanshu");
        user.setEmail("sudhanshu@example.com");
        return user;
    }
}
