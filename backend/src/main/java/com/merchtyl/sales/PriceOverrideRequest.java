package com.merchtyl.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record PriceOverrideRequest(
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        @NotNull SaleAdjustmentType type,
        @NotBlank String reasonCode,
        String reason) {}
