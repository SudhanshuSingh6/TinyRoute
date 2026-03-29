package com.tinyroute.service.url;

import com.tinyroute.dto.url.response.EditHistoryDTO;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.UrlEditHistory;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.InvalidUrlException;
import com.tinyroute.exception.LinkException;
import com.tinyroute.mapper.UrlMapper;
import com.tinyroute.repository.url.UrlEditHistoryRepository;
import com.tinyroute.repository.url.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlManagementServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private UrlEditHistoryRepository urlEditHistoryRepository;
    @Mock
    private UrlValidationService urlValidationService;
    @Mock
    private UrlMapper urlMapper;

    @InjectMocks
    private UrlManagementService urlManagementService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(10L);
        owner.setUsername("alice");
    }

    private void stubUrlMapperToDetailsResponse() {
        when(urlMapper.toUrlDetailsResponse(any(UrlMapping.class))).thenAnswer(inv -> {
            UrlMapping m = inv.getArgument(0);
            UrlDetailsResponse dto = new UrlDetailsResponse();
            dto.setId(m.getId());
            dto.setShortUrl(m.getShortUrl());
            dto.setOriginalUrl(m.getOriginalUrl());
            dto.setExpiresAt(m.getExpiresAt());
            dto.setStatus(m.getStatus());
            return dto;
        });
    }

    private void stubUrlMapperToEditHistoryResponse() {
        when(urlMapper.toEditHistoryResponse(any(UrlEditHistory.class))).thenAnswer(inv -> {
            UrlEditHistory h = inv.getArgument(0);
            EditHistoryDTO dto = new EditHistoryDTO();
            dto.setId(h.getId());
            dto.setOldUrl(h.getOldUrl());
            return dto;
        });
    }

    @Test
    void deleteUrl_deletesThroughRepository() {
        UrlMapping mapping = ownedActiveMapping(1L);
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));

        urlManagementService.deleteUrl(1L, "alice");

        verify(urlMappingRepository).delete(mapping);
    }

    @Test
    void deleteUrl_whenNotOwner_throwsAccessDenied() {
        UrlMapping mapping = ownedActiveMapping(1L);
        mapping.getUser().setUsername("bob");
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));

        assertThrows(LinkException.class, () -> urlManagementService.deleteUrl(1L, "alice"));
    }


    @Test
    void editUrl_whenSameDestination_skipsHistoryAndSave() {
        stubUrlMapperToDetailsResponse();
        UrlMapping mapping = ownedActiveMapping(1L);
        mapping.setOriginalUrl("https://example.com/welcome");
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));
        when(urlValidationService.validateAndNormalizeDestinationUrl("https://example.com/welcome"))
                .thenReturn("https://example.com/welcome");

        urlManagementService.editUrl(1L, "https://example.com/welcome", owner);

        verify(urlEditHistoryRepository, never()).save(any());
        verify(urlMappingRepository, never()).save(any());
    }

    @Test
    void editUrl_whenAnotherActiveLinkHasSameDestination_throwsConflict() {
        UrlMapping editing = ownedActiveMapping(1L);
        editing.setOriginalUrl("https://old.com");
        UrlMapping other = ownedActiveMapping(2L);
        other.setOriginalUrl("https://target.com");

        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(editing));
        when(urlValidationService.validateAndNormalizeDestinationUrl("https://target.com"))
                .thenReturn("https://target.com");
        when(urlMappingRepository.existsByOriginalUrlAndUserAndIdNot(
                "https://target.com",
                owner,
                1L
        )).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> urlManagementService.editUrl(1L, "https://target.com", owner));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals("URL_ALREADY_EXISTS", ex.getErrorCode());
    }

    @Test
    void editUrl_whenDestinationUnique_appendsHistoryAndSaves() {
        stubUrlMapperToDetailsResponse();
        UrlMapping editing = ownedActiveMapping(1L);
        editing.setOriginalUrl("https://old.com");
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(editing));
        when(urlValidationService.validateAndNormalizeDestinationUrl("https://new.com"))
                .thenReturn("https://new.com");

        when(urlMappingRepository.existsByOriginalUrlAndUserAndIdNot(
                "https://new.com",
                owner,
                1L
        )).thenReturn(false);
        when(urlMappingRepository.save(editing)).thenReturn(editing);

        urlManagementService.editUrl(1L, "https://new.com", owner);

        verify(urlEditHistoryRepository).save(any(UrlEditHistory.class));
        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(urlMappingRepository).save(captor.capture());
        assertEquals("https://new.com", captor.getValue().getOriginalUrl());
    }


    @Test
    void updateExpiry_whenPastDate_throwsInvalidUrl() {
        UrlMapping mapping = ownedActiveMapping(1L);
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));

        assertThrows(InvalidUrlException.class,
                () -> urlManagementService.updateExpiry(1L, LocalDateTime.now().minusMinutes(1), owner));
    }

    @Test
    void updateExpiry_whenExpiredAndNewExpiryInFuture_reactivates() {
        stubUrlMapperToDetailsResponse();
        UrlMapping mapping = ownedActiveMapping(1L);
        mapping.setStatus(UrlStatus.EXPIRED);
        mapping.setExpiresAt(LocalDateTime.now().minusDays(1));
        LocalDateTime newExpiry = LocalDateTime.now().plusDays(7);
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));
        when(urlMappingRepository.save(mapping)).thenReturn(mapping);

        urlManagementService.updateExpiry(1L, newExpiry, owner);

        assertEquals(UrlStatus.ACTIVE, mapping.getStatus());
        assertEquals(newExpiry, mapping.getExpiresAt());
    }


    @Test
    void getEditHistory_returnsMappedNewestFirst() {
        stubUrlMapperToEditHistoryResponse();
        UrlMapping mapping = ownedActiveMapping(1L);
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));
        UrlEditHistory h1 = new UrlEditHistory();
        h1.setId(1L);
        h1.setOldUrl("https://a.com");
        UrlEditHistory h2 = new UrlEditHistory();
        h2.setId(2L);
        h2.setOldUrl("https://b.com");
        when(urlEditHistoryRepository.findByUrlMappingOrderByChangedAtDesc(mapping)).thenReturn(List.of(h1, h2));

        List<EditHistoryDTO> result = urlManagementService.getEditHistory(1L, "alice");

        assertEquals(2, result.size());
        assertEquals("https://a.com", result.get(0).getOldUrl());
    }

    @Test
    void getEditHistory_whenNoEntries_returnsEmptyList() {
        UrlMapping mapping = ownedActiveMapping(1L);
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));
        when(urlEditHistoryRepository.findByUrlMappingOrderByChangedAtDesc(mapping)).thenReturn(List.of());

        assertTrue(urlManagementService.getEditHistory(1L, "alice").isEmpty());
    }

    private UrlMapping ownedActiveMapping(long id) {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(id);
        mapping.setShortUrl("s" + id);
        mapping.setUser(owner);
        mapping.setStatus(UrlStatus.ACTIVE);
        return mapping;
    }
}
