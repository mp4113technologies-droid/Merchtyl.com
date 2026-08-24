package com.merchtyl.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryStockReportRow(
        UUID storeId,
        String storeCode,
        String storeName,
        UUID productId,
        String productSku,
        String productName,
        UUID categoryId,
        BigDecimal cost,
        BigDecimal quantityOnHand,
        BigDecimal inventoryValue,
        Instant lastTransactionAt
) {
}
