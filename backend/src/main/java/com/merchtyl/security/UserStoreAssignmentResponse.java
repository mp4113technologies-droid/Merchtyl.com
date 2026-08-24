package com.merchtyl.security;

import java.time.Instant;
import java.util.UUID;

public record UserStoreAssignmentResponse(
        UUID id,
        UUID tenantId,
        UUID userId,
        UUID storeId,
        String storeCode,
        String storeName,
        AssignmentRole assignmentRole,
        AssignmentStatus status,
        boolean active,
        UUID assignedBy,
        Instant assignedAt,
        UUID removedBy,
        Instant removedAt,
        String removalReason,
        long version
) {
    public static UserStoreAssignmentResponse from(UserStoreAssignment assignment) {
        return new UserStoreAssignmentResponse(
                assignment.getId(),
                assignment.getTenantId(),
                assignment.getUser().getId(),
                assignment.getStore().getId(),
                assignment.getStore().getCode(),
                assignment.getStore().getName(),
                assignment.getAssignmentRole(),
                assignment.getStatus(),
                assignment.isActive(),
                assignment.getAssignedBy(),
                assignment.getAssignedAt(),
                assignment.getRemovedBy(),
                assignment.getRemovedAt(),
                assignment.getRemovalReason(),
                assignment.getVersion());
    }
}
