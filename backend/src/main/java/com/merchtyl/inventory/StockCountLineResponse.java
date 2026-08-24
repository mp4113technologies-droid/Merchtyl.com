package com.merchtyl.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockCountLineResponse(
        UUID id,
        UUID productId,
        BigDecimal expectedQuantity,
        BigDecimal countedQuantity,
        BigDecimal varianceQuantity,
        Long balanceVersion,
        BigDecimal resultingQuantity,
        UUID inventoryTransactionId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static StockCountLineResponse from(StockCountLine line) {
        return new StockCountLineResponse(
                line.getId(),
                line.getProduct().getId(),
                line.getExpectedQuantity(),
                line.getCountedQuantity(),
                line.getVarianceQuantity(),
                line.getBalanceVersion(),
                line.getResultingQuantity(),
                line.getInventoryTransactionId(),
                line.getCreatedAt(),
                line.getUpdatedAt(),
                line.getVersion());
    }
}
