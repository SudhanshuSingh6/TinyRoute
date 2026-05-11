package com.tinyroute.service.url;

import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.Role;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.InvalidUrlException;
import com.tinyroute.mapper.UrlMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
            dto.setMaxClicks(m.getMaxClicks());
            dto.setExpiresAt(m.getExpiresAt());
            return dto;
        });
    }
}