package com.merchtyl.product;

import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
