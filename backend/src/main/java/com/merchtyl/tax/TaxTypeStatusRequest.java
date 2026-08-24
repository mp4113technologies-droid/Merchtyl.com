package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record TaxTypeStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
