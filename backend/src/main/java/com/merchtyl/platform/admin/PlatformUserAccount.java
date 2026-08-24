package com.merchtyl.platform.admin;

import com.merchtyl.security.RoleName;

import java.time.Instant;
import java.util.UUID;

public record PlatformUserAccount(
        UUID id,
        String email,
        String displayName,
        String passwordHash,
        RoleName role,
        boolean enabled,
        boolean locked,
        boolean passwordChangeRequired,
        boolean testProvisioned,
        String testProvisioningReference,
        Instant testProvisionedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
