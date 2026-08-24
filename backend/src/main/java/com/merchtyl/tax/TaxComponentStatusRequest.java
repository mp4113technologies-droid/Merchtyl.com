package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record TaxComponentStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
