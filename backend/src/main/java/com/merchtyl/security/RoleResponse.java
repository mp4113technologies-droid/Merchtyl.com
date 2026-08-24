package com.merchtyl.security;

import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        RoleName name,
        String description,
        boolean systemRole,
        List<String> permissions,
        long version
) {
}
