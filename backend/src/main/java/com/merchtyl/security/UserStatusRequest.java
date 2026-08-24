package com.merchtyl.security;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
        @NotNull Boolean enabled,
        @NotNull Long version
) {
}
