package com.merchtyl.supplier;

import jakarta.validation.constraints.NotNull;

public record SupplierStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
