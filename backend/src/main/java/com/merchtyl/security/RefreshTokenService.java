package com.merchtyl.security;

import com.merchtyl.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public RefreshToken createRefreshToken(User user, String rawToken, Instant expiresAt) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken is required");
        }
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }

        return refreshTokenRepository.save(new RefreshToken(user, hashToken(rawToken), expiresAt));
    }

    public String generateRawToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findActiveToken(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .filter(token -> token.isActive(now));
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(hashToken(rawToken));
    }

    @Transactional
    public RefreshToken revoke(UUID tokenId, UUID replacedByTokenId, Instant revokedAt) {
        RefreshToken token = refreshTokenRepository.findById(tokenId)
                .orElseThrow(() -> new NotFoundException("Refresh token not found"));
        token.revoke(revokedAt == null ? Instant.now() : revokedAt, replacedByTokenId);
        return token;
    }

    @Transactional
    public void revokeActiveTokensForUser(User user, Instant revokedAt) {
        Instant effectiveRevokedAt = revokedAt == null ? Instant.now() : revokedAt;
        refreshTokenRepository.findByUserAndRevokedAtIsNull(user).forEach(token -> {
            if (token.getExpiresAt().isAfter(effectiveRevokedAt)) {
                token.revoke(effectiveRevokedAt, null);
            }
        });
    }

    String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
