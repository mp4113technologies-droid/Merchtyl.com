package com.merchtyl.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesReportResponse(
        UUID storeId,
        UUID registerId,
        UUID cashierId,
        UUID categoryId,
        UUID productId,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal grossSales,
        BigDecimal netSales,
        BigDecimal discounts,
        BigDecimal refunds,
        BigDecimal taxes,
        BigDecimal payments,
        long saleCount,
        long refundCount,
        List<SalesReportPaymentBreakdown> paymentBreakdown,
        Instant generatedAt
) {
}
