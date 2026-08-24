package com.merchtyl.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(refreshTokenRepository);

    @Test
    void createRefreshTokenStoresOnlyHash() {
        User user = new User("cashier@example.local", "Cashier", "$2a$10$hash");
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(refreshTokenRepository.save(org.mockito.ArgumentMatchers.any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken token = service.createRefreshToken(user, "raw-refresh-token", expiresAt);

        assertThat(token.getTokenHash()).isNotEqualTo("raw-refresh-token");
        assertThat(token.getTokenHash()).hasSize(64);
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.isActive(Instant.now())).isTrue();
    }

    @Test
    void findActiveTokenReturnsOnlyUnexpiredUnrevokedToken() {
        User user = new User("cashier@example.local", "Cashier", "$2a$10$hash");
        RefreshToken token = new RefreshToken(
                user,
                service.hashToken("raw-refresh-token"),
                Instant.parse("2026-07-21T15:00:00Z"));
        when(refreshTokenRepository.findByTokenHash(service.hashToken("raw-refresh-token")))
                .thenReturn(Optional.of(token));

        Optional<RefreshToken> found = service.findActiveToken(
                "raw-refresh-token",
                Instant.parse("2026-07-21T14:00:00Z"));

        assertThat(found).contains(token);
    }
}
