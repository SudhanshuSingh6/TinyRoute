package com.tinyroute.auth.service;

import com.tinyroute.auth.entity.RefreshToken;
import com.tinyroute.user.entity.User;
import com.tinyroute.exception.ApiException;
import com.tinyroute.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;


    @Transactional
    public String createRefreshToken(User user) {
        String rawToken  = generateRawToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(tokenHash);
        entity.setUser(user);
        entity.setExpiresAt(LocalDateTime.now().plus(refreshTokenExpirationMs, ChronoUnit.MILLIS));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    @Transactional
    public RotationResult rotateToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid."
                ));

        if (existing.isRevoked()) {
            log.warn("Revoked refresh token reused for user id={}. Revoking all tokens.",
                    existing.getUser().getId());
            refreshTokenRepository.revokeAllActiveForUser(existing.getUser());
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_REVOKED",
                    "Refresh token has already been used. Please log in again."
            );
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_EXPIRED",
                    "Refresh token has expired. Please log in again."
            );
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        User user        = existing.getUser();
        String newRaw    = generateRawToken();
        String newHash   = hashToken(newRaw);

        RefreshToken newToken = new RefreshToken();
        newToken.setTokenHash(newHash);
        newToken.setUser(user);
        newToken.setExpiresAt(LocalDateTime.now().plus(refreshTokenExpirationMs, ChronoUnit.MILLIS));
        newToken.setCreatedAt(LocalDateTime.now());
        newToken.setRevoked(false);
        refreshTokenRepository.save(newToken);

        return new RotationResult(user, newRaw);
    }

    @Transactional
    public void revoke(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllActiveForUser(user);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record RotationResult(User user, String newRawRefreshToken) {}
}