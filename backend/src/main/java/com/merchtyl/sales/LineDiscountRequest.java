package com.merchtyl.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record LineDiscountRequest(
        @NotNull @Positive BigDecimal value,
        @NotNull SaleAdjustmentType type,
        @NotBlank String reasonCode,
        String reason) {}
