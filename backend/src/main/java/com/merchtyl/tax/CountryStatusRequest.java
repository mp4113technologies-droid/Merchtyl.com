package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record CountryStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
