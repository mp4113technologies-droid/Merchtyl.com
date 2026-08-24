package com.merchtyl.security;

import java.util.Objects;

public record CreateSecurityUserCommand(
        String email,
        String displayName,
        String rawPassword,
        RoleName initialRole) {
    public CreateSecurityUserCommand {
        requireText(email, "email");
        requireText(displayName, "displayName");
        requireText(rawPassword, "rawPassword");
        Objects.requireNonNull(initialRole, "initialRole is required");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
