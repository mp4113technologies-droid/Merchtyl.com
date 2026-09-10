package com.merchtyl.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaleForceCloseRequest(
        @NotNull Long version,
        @NotBlank String reasonCode,
        String note) {
}
