package com.merchtyl.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReportRequest(
        UUID storeId,
        UUID categoryId,
        UUID productId,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal lowStockThreshold
) {
}
