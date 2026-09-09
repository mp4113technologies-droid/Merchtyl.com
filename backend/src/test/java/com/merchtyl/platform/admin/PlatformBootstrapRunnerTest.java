package com.merchtyl.platform.admin;

import com.merchtyl.audit.AuditService;
import com.merchtyl.auth.PasswordPolicyService;
import com.merchtyl.config.PlatformAdministrationProperties;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformBootstrapRunnerTest {
    @Mock PlatformUserRepository platformUsers;
    @Mock UserRepository tenantUsers;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @Mock PasswordPolicyService passwordPolicy;
    @Mock ApplicationArguments arguments;

    @Test
    void disabledBootstrapDoesNothing() {
        runner(false, "admin@example.test", "ValidPass1!").run(arguments);

        verifyNoInteractions(platformUsers, tenantUsers, passwordEncoder, auditService, passwordPolicy);
    }

    @Test
    void createsOneEncodedTemporarySuperAdminWhenNoneExists() {
        when(platformUsers.findByEmail("admin@example.test")).thenReturn(Optional.empty());
        when(tenantUsers.existsByEmailIgnoreCase("admin@example.test")).thenReturn(false);
        when(passwordEncoder.encode("ValidPass1!")).thenReturn("encoded-password");
        when(platformUsers.create(eq("admin@example.test"), eq("Merchtyl Administrator"), eq("encoded-password"),
                eq(RoleName.PLATFORM_SUPER_ADMIN), eq(true), eq(true))).thenReturn(account("admin@example.test", RoleName.PLATFORM_SUPER_ADMIN));

        runner(true, "Admin@Example.Test", "ValidPass1!").run(arguments);

        verify(platformUsers).acquireBootstrapLock();
        verify(passwordPolicy).validate("ValidPass1!");
        verify(platformUsers).create("admin@example.test", "Merchtyl Administrator", "encoded-password",
                RoleName.PLATFORM_SUPER_ADMIN, true, true);
        verify(auditService).record(any());
    }

    @Test
    void existingSuperAdminSkipsWithoutCreatingAnotherEvenIfDisabled() {
        when(platformUsers.existsSuperAdmin()).thenReturn(true);

        runner(true, "other@example.test", "ValidPass1!").run(arguments);

        verify(platformUsers).acquireBootstrapLock();
        verify(platformUsers, never()).create(any(), any(), any(), any(), eq(true), eq(true));
        verifyNoInteractions(tenantUsers, passwordEncoder, auditService, passwordPolicy);
    }

    @Test
    void existingNonSuperAdminPlatformEmailIsNeverElevated() {
        when(platformUsers.findByEmail("admin@example.test"))
                .thenReturn(Optional.of(account("admin@example.test", RoleName.PLATFORM_SUPPORT_ADMIN)));

        assertThatThrownBy(() -> runner(true, "admin@example.test", "ValidPass1!").run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-Super-Admin platform account");

        verify(platformUsers, never()).create(any(), any(), any(), any(), eq(true), eq(true));
    }

    @Test
    void merchantEmailIsNeverElevatedToPlatformAdmin() {
        when(platformUsers.findByEmail("admin@example.test")).thenReturn(Optional.empty());
        when(tenantUsers.existsByEmailIgnoreCase("admin@example.test")).thenReturn(true);

        assertThatThrownBy(() -> runner(true, "admin@example.test", "ValidPass1!").run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("merchant account");

        verify(platformUsers, never()).create(any(), any(), any(), any(), eq(true), eq(true));
    }

    private PlatformBootstrapRunner runner(boolean enabled, String email, String password) {
        var properties = new PlatformAdministrationProperties(
                new PlatformAdministrationProperties.Bootstrap(enabled, email, "Merchtyl Administrator", password), null, null);
        return new PlatformBootstrapRunner(properties, platformUsers, passwordEncoder, auditService, passwordPolicy, tenantUsers);
    }

    private static PlatformUserAccount account(String email, RoleName role) {
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        return new PlatformUserAccount(UUID.randomUUID(), email, "Merchtyl Administrator", "encoded-password", role,
                true, false, true, false, null, null, now, now, 0);
    }
}
