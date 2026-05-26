package com.tinyroute.service.url;

import com.tinyroute.url.dto.UrlDetailsResponse;
import com.tinyroute.user.entity.Role;
import com.tinyroute.url.entity.UrlMapping;
import com.tinyroute.user.entity.User;
import com.tinyroute.url.mapper.UrlMapper;
import com.tinyroute.url.repository.UrlMappingRepository;
import com.tinyroute.url.service.UrlCreationService;
import com.tinyroute.url.service.UrlValidationService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private void stubValidation(String rawUrl, String normalized) {
        when(urlValidationService.validateAndNormalizeDestinationUrl(rawUrl)).thenReturn(normalized);
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
}