package com.merchtyl.register;

import jakarta.validation.constraints.NotNull;

public record RegisterStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
