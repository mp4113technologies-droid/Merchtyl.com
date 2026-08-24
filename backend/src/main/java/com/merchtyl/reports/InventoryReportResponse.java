package com.merchtyl.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InventoryReportResponse(
        UUID storeId,
        UUID categoryId,
        UUID productId,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal lowStockThreshold,
        BigDecimal currentStock,
        BigDecimal inventoryValue,
        long stockItemCount,
        long lowStockCount,
        long negativeStockCount,
        long adjustmentCount,
        long damagedCount,
        long expiredCount,
        BigDecimal adjustmentQuantity,
        BigDecimal damagedQuantity,
        BigDecimal expiredQuantity,
        BigDecimal adjustmentValue,
        BigDecimal damagedValue,
        BigDecimal expiredValue,
        List<InventoryStockReportRow> stockRows,
        List<InventoryStockReportRow> lowStockRows,
        List<InventoryStockReportRow> negativeStockRows,
        List<InventoryActivityReportRow> adjustmentRows,
        List<InventoryActivityReportRow> damagedRows,
        List<InventoryActivityReportRow> expiredRows,
        Instant generatedAt
) {
}
