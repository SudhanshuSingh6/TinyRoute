package com.tinyroute.service.auth;

import com.tinyroute.auth.entity.RefreshToken;
import com.tinyroute.auth.repository.RefreshTokenRepository;
import com.tinyroute.auth.service.RefreshTokenService;
import com.tinyroute.exception.ApiException;
import com.tinyroute.exception.ErrorCodes;
import com.tinyroute.user.entity.Role;
import com.tinyroute.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationMs", 604_800_000L); // 7 days
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setRole(Role.ROLE_USER);
    }

    private RefreshToken existingToken(boolean revoked, LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        token.setTokenHash("stored-hash");
        return token;
    }

    @Test
    void rotateToken_validToken_returnsNewTokenAndUser() {
        RefreshToken existing = existingToken(false, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        RefreshTokenService.RotationResult result = refreshTokenService.rotateToken("raw-token");

        assertEquals(user, result.user());
        assertNotNull(result.newRawRefreshToken());
        assertFalse(result.newRawRefreshToken().isBlank());
        assertTrue(existing.isRevoked());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class)); // revoke old + save new
    }

    @Test
    void rotateToken_revokedToken_revokesAllAndThrows() {
        RefreshToken existing = existingToken(true, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        ApiException ex = assertThrows(ApiException.class,
                () -> refreshTokenService.rotateToken("raw-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
        assertEquals(ErrorCodes.REFRESH_TOKEN_REVOKED, ex.getErrorCode());
        verify(refreshTokenRepository).revokeAllActiveForUser(user);
    }

    @Test
    void rotateToken_expiredToken_throwsExpired() {
        RefreshToken existing = existingToken(false, LocalDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        ApiException ex = assertThrows(ApiException.class,
                () -> refreshTokenService.rotateToken("raw-token"));

        assertEquals(ErrorCodes.REFRESH_TOKEN_EXPIRED, ex.getErrorCode());
    }

    @Test
    void rotateToken_unknownToken_throwsInvalid() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> refreshTokenService.rotateToken("raw-token"));

        assertEquals(ErrorCodes.INVALID_REFRESH_TOKEN, ex.getErrorCode());
    }

    @Test
    void createRefreshToken_storesHashNotRawToken() {
        String raw = refreshTokenService.createRefreshToken(user);

        assertNotNull(raw);
        assertFalse(raw.isBlank());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertEquals(user, saved.getUser());
        assertFalse(saved.isRevoked());
        assertNotEquals(raw, saved.getTokenHash(), "raw token must never be stored directly");
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void revoke_existingToken_marksRevoked() {
        RefreshToken existing = existingToken(false, LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        refreshTokenService.revoke("raw-token");

        assertTrue(existing.isRevoked());
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void revoke_unknownToken_doesNothing() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        refreshTokenService.revoke("raw-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void hashToken_isDeterministicForSameInput() {
        assertEquals(refreshTokenService.hashToken("abc"), refreshTokenService.hashToken("abc"));
    }

    @Test
    void hashToken_differsForDifferentInput() {
        assertNotEquals(refreshTokenService.hashToken("abc"), refreshTokenService.hashToken("xyz"));
    }
}
