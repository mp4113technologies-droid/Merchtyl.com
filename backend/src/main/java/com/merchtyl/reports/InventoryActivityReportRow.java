package com.merchtyl.reports;

import com.merchtyl.inventory.InventoryTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryActivityReportRow(
        UUID id,
        UUID storeId,
        String storeCode,
        String storeName,
        UUID productId,
        String productSku,
        String productName,
        UUID categoryId,
        InventoryTransactionType transactionType,
        BigDecimal quantityDelta,
        BigDecimal quantity,
        BigDecimal inventoryValue,
        String referenceType,
        UUID referenceId,
        String reason,
        UUID actorUserId,
        Instant occurredAt
) {
}
