package com.merchtyl.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockAdjustmentLineResponse(
        UUID id,
        UUID productId,
        StockAdjustmentType adjustmentType,
        BigDecimal quantity,
        BigDecimal quantityDelta,
        BigDecimal resultingQuantity,
        UUID inventoryTransactionId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static StockAdjustmentLineResponse from(StockAdjustmentLine line) {
        return new StockAdjustmentLineResponse(
                line.getId(),
                line.getProduct().getId(),
                line.getAdjustmentType(),
                line.getQuantity(),
                line.getQuantityDelta(),
                line.getResultingQuantity(),
                line.getInventoryTransactionId(),
                line.getCreatedAt(),
                line.getUpdatedAt(),
                line.getVersion());
    }
}
