package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record TaxJurisdictionStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
