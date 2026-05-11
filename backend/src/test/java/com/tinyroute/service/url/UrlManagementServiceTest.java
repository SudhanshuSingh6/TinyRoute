package com.tinyroute.service.url;

import com.tinyroute.dto.url.request.UpdateShortUrlRequest;
import com.tinyroute.dto.url.response.EditHistoryDTO;
import com.tinyroute.dto.url.response.UrlDetailsResponse;
import com.tinyroute.entity.UrlEditHistory;
import com.tinyroute.entity.UrlMapping;
import com.tinyroute.entity.UrlStatus;
import com.tinyroute.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.InvalidUrlException;
import com.tinyroute.exception.UrlException;
import com.tinyroute.mapper.UrlMapper;
import com.tinyroute.repository.analytics.ClickEventRepository;
import com.tinyroute.repository.analytics.UrlUniqueVisitorRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlManagementServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private UrlEditHistoryRepository urlEditHistoryRepository;
    @Mock
    private ClickEventRepository clickEventRepository;
    @Mock
    private UrlUniqueVisitorRepository urlUniqueVisitorRepository;
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

    // ─── helpers ────────────────────────────────────────────────────────────────

    private UrlMapping ownedActiveMapping(String shortUrl) {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(Long.parseLong(shortUrl.replaceAll("[^0-9]", "1")));
        mapping.setShortUrl(shortUrl);
        mapping.setUser(owner);
        mapping.setStatus(UrlStatus.ACTIVE);
        mapping.setOriginalUrl("https://original.com");
        return mapping;
    }

    private void stubFindByShortUrlAndUserId(String shortUrl, UrlMapping mapping) {
        when(urlMappingRepository.findByShortUrlAndUserId(shortUrl, 10L))
                .thenReturn(Optional.of(mapping));
    }

    private void stubMapper() {
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

    private UpdateShortUrlRequest editRequest(String originalUrl, String title) {
        UpdateShortUrlRequest req = new UpdateShortUrlRequest();
        req.setOriginalUrl(originalUrl);
        req.setTitle(title);
        return req;
    }

    // ─── deleteUrl ───────────────────────────────────────────────────────────────

    @Test
    void deleteUrl_deletesThroughRepository() {
        UrlMapping mapping = ownedActiveMapping("s1");
        stubFindByShortUrlAndUserId("s1", mapping);

        urlManagementService.deleteUrl("s1", owner.getId());

        verify(urlMappingRepository).delete(mapping);
        verify(clickEventRepository).deleteByUrlMapping(mapping);
        verify(urlUniqueVisitorRepository).deleteByUrlMapping(mapping);
        verify(urlEditHistoryRepository).deleteByUrlMapping(mapping);
    }

    @Test
    void deleteUrl_whenNotOwner_throwsLinkException() {
        // User tries to delete a URL that does not belong to her
        when(urlMappingRepository.findByShortUrlAndUserId("s2", owner.getId()))
                .thenReturn(Optional.empty());

        assertThrows(UrlException.class, () -> urlManagementService.deleteUrl("s2", owner.getId()));
        verify(urlMappingRepository, never()).delete(any());
    }

    // ─── editUrl ─────────────────────────────────────────────────────────────────

    @Test
    void editUrl_whenSameDestinationAndSameTitle_skipsHistoryAndSave() {
        stubMapper();
        UrlMapping mapping = ownedActiveMapping("s3");
        mapping.setOriginalUrl("https://example.com/welcome");
        stubFindByShortUrlAndUserId("s3", mapping);
        when(urlValidationService.validateAndNormalizeDestinationUrl("https://example.com/welcome"))
                .thenReturn("https://example.com/welcome");

        urlManagementService.editUrl("s3", editRequest("https://example.com/welcome", null), owner.getId());

        verify(urlEditHistoryRepository, never()).save(any());
        verify(urlMappingRepository, never()).save(any());
    }

    @Test
    void editUrl_whenAnotherActiveLinkHasSameDestination_throwsConflict() {
        UrlMapping editing = ownedActiveMapping("s4");
        editing.setOriginalUrl("https://old.com");
        stubFindByShortUrlAndUserId("s4", editing);
        when(urlValidationService.validateAndNormalizeDestinationUrl("https://target.com"))
                .thenReturn("https://target.com");
        when(urlMappingRepository.existsByOriginalUrlAndUserAndIdNot("https://target.com", owner, editing.getId()))
                .thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> urlManagementService.editUrl("s4", editRequest("https://target.com", null), owner.getId()));

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals("URL_ALREADY_EXISTS", ex.getErrorCode());
    }

    @Test
    void editUrl_whenDestinationChanges_appendsHistoryAndSaves() {
        stubMapper();
        UrlMapping editing = ownedActiveMapping("s5");
        editing.setOriginalUrl("https://old.com");
        stubFindByShortUrlAndUserId("s5", editing);
        when(urlValidationService.validateAndNormalizeDestinationUrl("https://new.com"))
                .thenReturn("https://new.com");
        when(urlMappingRepository.existsByOriginalUrlAndUserAndIdNot("https://new.com", owner, editing.getId()))
                .thenReturn(false);
        when(urlMappingRepository.save(editing)).thenReturn(editing);

        urlManagementService.editUrl("s5", editRequest("https://new.com", null), owner.getId());

        verify(urlEditHistoryRepository).save(any(UrlEditHistory.class));
        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(urlMappingRepository).save(captor.capture());
        assertEquals("https://new.com", captor.getValue().getOriginalUrl());
    }

    @Test
    void editUrl_whenOnlyTitleChanges_savesWithoutHistoryEntry() {
        stubMapper();
        UrlMapping editing = ownedActiveMapping("s6");
        editing.setOriginalUrl("https://same.com");
        editing.setTitle("Old Title");
        stubFindByShortUrlAndUserId("s6", editing);
        when(urlValidationService.validateAndNormalizeDestinationUrl("https://same.com"))
                .thenReturn("https://same.com");
        when(urlMappingRepository.save(editing)).thenReturn(editing);

        urlManagementService.editUrl("s6", editRequest("https://same.com", "New Title"), owner.getId());

        verify(urlEditHistoryRepository, never()).save(any());
        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(urlMappingRepository).save(captor.capture());
        assertEquals("New Title", captor.getValue().getTitle());
    }

    @Test
    void editUrl_whenTitleIsBlank_normalizesTitleToNull() {
        stubMapper();
        UrlMapping editing = ownedActiveMapping("s6b");
        editing.setOriginalUrl("https://same.com");
        editing.setTitle("Existing Title");
        stubFindByShortUrlAndUserId("s6b", editing);
        when(urlValidationService.validateAndNormalizeDestinationUrl("https://same.com"))
                .thenReturn("https://same.com");
        when(urlMappingRepository.save(editing)).thenReturn(editing);

        urlManagementService.editUrl("s6b", editRequest("https://same.com", "   "), owner.getId());

        verify(urlEditHistoryRepository, never()).save(any());
        verify(urlMappingRepository).save(editing);
        assertNull(editing.getTitle());
    }

    // ─── updateExpiry ────────────────────────────────────────────────────────────

    @Test
    void updateExpiry_whenPastDate_throwsInvalidUrl() {
        UrlMapping mapping = ownedActiveMapping("s7");
        stubFindByShortUrlAndUserId("s7", mapping);

        assertThrows(InvalidUrlException.class,
                () -> urlManagementService.updateExpiry("s7", LocalDateTime.now().minusMinutes(1), owner.getId()));
    }

    @Test
    void updateExpiry_whenExpiredAndNewExpiryInFuture_reactivates() {
        stubMapper();
        UrlMapping mapping = ownedActiveMapping("s8");
        mapping.setStatus(UrlStatus.EXPIRED);
        mapping.setExpiresAt(LocalDateTime.now().minusDays(1));
        LocalDateTime newExpiry = LocalDateTime.now().plusDays(7);
        stubFindByShortUrlAndUserId("s8", mapping);
        when(urlMappingRepository.save(mapping)).thenReturn(mapping);

        urlManagementService.updateExpiry("s8", newExpiry, owner.getId());

        assertEquals(UrlStatus.ACTIVE, mapping.getStatus());
        assertEquals(newExpiry, mapping.getExpiresAt());
    }

    @Test
    void updateExpiry_whenExpiredAndClickLimitAlreadyHit_reactivatesToClickLimitReached() {
        stubMapper();
        UrlMapping mapping = ownedActiveMapping("s9");
        mapping.setStatus(UrlStatus.EXPIRED);
        mapping.setClickCount(10);
        mapping.setMaxClicks(10);
        mapping.setExpiresAt(LocalDateTime.now().minusDays(1));
        LocalDateTime newExpiry = LocalDateTime.now().plusDays(7);
        stubFindByShortUrlAndUserId("s9", mapping);
        when(urlMappingRepository.save(mapping)).thenReturn(mapping);

        urlManagementService.updateExpiry("s9", newExpiry, owner.getId());

        // Should not be ACTIVE because click limit is already hit
        assertEquals(UrlStatus.CLICK_LIMIT_REACHED, mapping.getStatus());
    }

    @Test
    void updateExpiry_whenSameExpiryProvided_doesNotSave() {
        stubMapper();
        LocalDateTime expiry = LocalDateTime.now().plusDays(5);
        UrlMapping mapping = ownedActiveMapping("s10");
        mapping.setExpiresAt(expiry);
        stubFindByShortUrlAndUserId("s10", mapping);

        urlManagementService.updateExpiry("s10", expiry, owner.getId());

        verify(urlMappingRepository, never()).save(any());
    }

    // ─── disableUrl ──────────────────────────────────────────────────────────────

    @Test
    void disableUrl_whenActive_setsStatusToDisabled() {
        stubMapper();
        UrlMapping mapping = ownedActiveMapping("s11");
        stubFindByShortUrlAndUserId("s11", mapping);
        when(urlMappingRepository.save(mapping)).thenReturn(mapping);

        urlManagementService.disableUrl("s11", owner.getId());

        assertEquals(UrlStatus.DISABLED, mapping.getStatus());
        verify(urlMappingRepository).save(mapping);
    }

    @Test
    void disableUrl_whenAlreadyDisabled_throwsBadRequest() {
        UrlMapping mapping = ownedActiveMapping("s12");
        mapping.setStatus(UrlStatus.DISABLED);
        stubFindByShortUrlAndUserId("s12", mapping);

        ApiException ex = assertThrows(ApiException.class,
                () -> urlManagementService.disableUrl("s12", owner.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    // ─── enableUrl ───────────────────────────────────────────────────────────────

    @Test
    void enableUrl_whenDisabled_setsStatusToActive() {
        stubMapper();
        UrlMapping mapping = ownedActiveMapping("s13");
        mapping.setStatus(UrlStatus.DISABLED);
        stubFindByShortUrlAndUserId("s13", mapping);
        when(urlMappingRepository.save(mapping)).thenReturn(mapping);

        urlManagementService.enableUrl("s13", owner.getId());

        assertEquals(UrlStatus.ACTIVE, mapping.getStatus());
    }

    @Test
    void enableUrl_whenNotDisabled_throwsBadRequest() {
        UrlMapping mapping = ownedActiveMapping("s14");
        mapping.setStatus(UrlStatus.ACTIVE);
        stubFindByShortUrlAndUserId("s14", mapping);

        ApiException ex = assertThrows(ApiException.class,
                () -> urlManagementService.enableUrl("s14", owner.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals("URL_ENABLE_INVALID", ex.getErrorCode());
    }

    @Test
    void enableUrl_whenDisabledButExpired_throwsBadRequest() {
        UrlMapping mapping = ownedActiveMapping("s15");
        mapping.setStatus(UrlStatus.DISABLED);
        mapping.setExpiresAt(LocalDateTime.now().minusDays(1));
        stubFindByShortUrlAndUserId("s15", mapping);

        ApiException ex = assertThrows(ApiException.class,
                () -> urlManagementService.enableUrl("s15", owner.getId()));
        assertEquals(HttpStatus.GONE, ex.getHttpStatus());
        assertEquals("URL_EXPIRED", ex.getErrorCode());
    }

    @Test
    void enableUrl_whenDisabledAndClickLimitHit_throwsBadRequest() {
        UrlMapping mapping = ownedActiveMapping("s16");
        mapping.setStatus(UrlStatus.DISABLED);
        mapping.setClickCount(100);
        mapping.setMaxClicks(100);
        stubFindByShortUrlAndUserId("s16", mapping);

        ApiException ex = assertThrows(ApiException.class,
                () -> urlManagementService.enableUrl("s16", owner.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals("CLICK_LIMIT_REACHED", ex.getErrorCode());
    }

    // ─── getEditHistory ──────────────────────────────────────────────────────────

    @Test
    void getEditHistory_returnsMappedNewestFirst() {
        when(urlMapper.toEditHistoryResponse(any(UrlEditHistory.class))).thenAnswer(inv -> {
            UrlEditHistory h = inv.getArgument(0);
            EditHistoryDTO dto = new EditHistoryDTO();
            dto.setId(h.getId());
            dto.setOldUrl(h.getOldUrl());
            return dto;
        });
        UrlMapping mapping = ownedActiveMapping("s17");
        stubFindByShortUrlAndUserId("s17", mapping);

        UrlEditHistory h1 = new UrlEditHistory();
        h1.setId(1L);
        h1.setOldUrl("https://a.com");
        UrlEditHistory h2 = new UrlEditHistory();
        h2.setId(2L);
        h2.setOldUrl("https://b.com");
        when(urlEditHistoryRepository.findByUrlMappingOrderByChangedAtDesc(mapping))
                .thenReturn(List.of(h1, h2));

        List<EditHistoryDTO> result = urlManagementService.getEditHistory("s17", owner.getId());

        assertEquals(2, result.size());
        assertEquals("https://a.com", result.get(0).getOldUrl());
        assertEquals("https://b.com", result.get(1).getOldUrl());
    }

    @Test
    void getEditHistory_whenNoEntries_returnsEmptyList() {
        UrlMapping mapping = ownedActiveMapping("s18");
        stubFindByShortUrlAndUserId("s18", mapping);
        when(urlEditHistoryRepository.findByUrlMappingOrderByChangedAtDesc(mapping))
                .thenReturn(List.of());

        assertTrue(urlManagementService.getEditHistory("s18", owner.getId()).isEmpty());
    }
}
