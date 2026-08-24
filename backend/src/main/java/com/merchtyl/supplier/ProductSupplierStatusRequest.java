package com.merchtyl.supplier;

import jakarta.validation.constraints.NotNull;

public record ProductSupplierStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
