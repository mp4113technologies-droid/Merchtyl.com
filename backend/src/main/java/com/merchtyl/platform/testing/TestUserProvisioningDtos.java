package com.merchtyl.platform.testing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TestUserProvisioningDtos {
    private TestUserProvisioningDtos() {
    }

    public record ProvisionUserRequest(
            String tenantCode,
            TestUserProvisioningRole role,
            String firstName,
            String lastName,
            String email,
            String password,
            TestUserProvisioningStatus status,
            List<String> storeCodes,
            Boolean mustChangePassword,
            Boolean createTenantIfMissing,
            Boolean createStoresIfMissing,
            Integer quantity,
            Boolean generateRandomData,
            String emailPrefix,
            String defaultPassword,
            Long randomSeed,
            TestUserExistingStrategy onExisting
    ) {
    }

    public record BatchProvisionUsersRequest(
            List<ProvisionUserRequest> users
    ) {
    }

    public record CleanupRequest(
            String tenantCode,
            String emailPattern,
            TestUserProvisioningRole role,
            Instant createdBefore,
            Boolean testProvisionedOnly
    ) {
    }

    public record ProvisionUserResponse(
            UUID id,
            String accountScope,
            String tenantCode,
            TestUserProvisioningRole role,
            String email,
            TestUserProvisioningStatus status,
            List<String> assignedStores,
            boolean created,
            boolean passwordAccepted
    ) {
    }

    public record BatchProvisionUsersResponse(
            List<ProvisionUserResponse> users
    ) {
    }

    public record CleanupResponse(
            int disabledUsers
    ) {
    }
}
