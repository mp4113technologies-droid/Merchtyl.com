package com.merchtyl.security;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UserStoreAssignmentRequest(
        @NotNull List<UUID> storeIds,
        @NotNull AssignmentRole assignmentRole,
        String removalReason
) {
}
