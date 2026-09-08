package com.merchtyl.inventory;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record StockAdjustmentLineRequest(
        @NotNull UUID productId,
        @NotNull StockAdjustmentType adjustmentType,
        @NotNull BigDecimal quantity,
        Long balanceVersion,
        UUID variantId
) {
    public StockAdjustmentLineRequest(UUID productId, StockAdjustmentType adjustmentType, BigDecimal quantity, Long balanceVersion) {
        this(productId, adjustmentType, quantity, balanceVersion, null);
    }
}
