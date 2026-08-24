package com.merchtyl.security;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UserRolesRequest(
        @NotEmpty List<RoleName> roles,
        List<UUID> storeIds,
        List<UUID> registerIds,
        @NotNull Long version
) {
}
