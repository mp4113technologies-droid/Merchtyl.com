package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record TaxRateStatusRequest(
        @NotNull TaxRateStatus status,
        @NotNull Long version
) {
}
