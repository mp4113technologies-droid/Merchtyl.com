package com.merchtyl.inventory;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record StockAdjustmentLineRequest(
        @NotNull UUID productId,
        @NotNull StockAdjustmentType adjustmentType,
        @NotNull BigDecimal quantity,
        Long balanceVersion
) {
}
