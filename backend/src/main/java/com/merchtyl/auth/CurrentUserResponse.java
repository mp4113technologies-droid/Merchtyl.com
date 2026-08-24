package com.merchtyl.auth;

import com.merchtyl.security.RoleName;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String email,
        String displayName,
        List<RoleName> roles,
        List<String> permissions
) {
}
