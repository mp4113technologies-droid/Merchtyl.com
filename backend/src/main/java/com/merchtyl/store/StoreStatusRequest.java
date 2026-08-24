package com.merchtyl.store;

import jakarta.validation.constraints.NotNull;

public record StoreStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
