package com.merchtyl.sales;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SaleUpdateQuantityRequest(
        @NotNull BigDecimal quantity
) {
}
