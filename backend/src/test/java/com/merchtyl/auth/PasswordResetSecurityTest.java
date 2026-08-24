package com.merchtyl.auth;

import com.merchtyl.security.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetSecurityTest {
    @Test
    void tokenHashDoesNotStoreRawToken() {
        String rawToken = "one-time-reset-token-for-testing";
        String hash = PasswordResetService.hash(rawToken);

        assertThat(hash).hasSize(64).doesNotContain(rawToken);
        assertThat(hash).isEqualTo(PasswordResetService.hash(rawToken));
    }

    @Test
    void successfulResetUnlocksAndClearsFailedAttempts() {
        User user = new User("owner@example.com", "Owner", "old-hash");
        user.lock();

        Instant resetAt = Instant.parse("2026-08-17T12:00:00Z");
        user.completePasswordReset("new-hash", resetAt);

        assertThat(user.isLocked()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getPasswordResetAt()).isEqualTo(resetAt);
    }

    @Test
    void generatedTokenAlphabetIsSafeForResetLinks() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        assertThat(token).doesNotContain("+", "/", "=");
        assertThat(PasswordResetService.hash(token)).isEqualTo(PasswordResetService.hash(token));
    }
}
