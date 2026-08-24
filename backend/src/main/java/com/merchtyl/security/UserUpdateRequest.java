package com.merchtyl.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UserUpdateRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 160) String displayName,
        boolean locked,
        List<UUID> storeIds,
        List<UUID> registerIds,
        @NotNull Long version
) {
}
