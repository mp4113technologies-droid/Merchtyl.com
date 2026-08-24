package com.merchtyl.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        boolean enabled,
        boolean locked,
        List<RoleName> roles,
        List<UUID> storeIds,
        List<UUID> registerIds,
        String status,
        List<UserStoreAssignmentResponse> storeAssignments,
        UUID createdByUserId,
        RoleName createdByRole,
        UUID updatedByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public UserResponse(
            UUID id,
            String email,
            String displayName,
            boolean enabled,
            boolean locked,
            List<RoleName> roles,
            List<UUID> storeIds,
            List<UUID> registerIds,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        this(id, email, displayName, enabled, locked, roles, storeIds, registerIds,
                locked ? "LOCKED" : enabled ? "ACTIVE" : "DISABLED", List.of(), null, null, null, createdAt, updatedAt, version);
    }
}
