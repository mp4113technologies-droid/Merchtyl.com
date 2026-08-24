package com.merchtyl.security;

import java.util.UUID;

public record UserSearchRequest(
        String email,
        String displayName,
        RoleName role,
        UUID storeId,
        UUID registerId,
        String status,
        Boolean enabled,
        Boolean locked,
        UUID createdByUserId,
        String search,
        int page,
        int size
) {
}
