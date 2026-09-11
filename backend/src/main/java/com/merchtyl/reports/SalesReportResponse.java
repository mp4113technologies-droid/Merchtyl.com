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
        BigDecimal containerDeposits,
        BigDecimal refundedContainerDeposits,
        long saleCount,
        long refundCount,
        List<SalesReportPaymentBreakdown> paymentBreakdown,
        Instant generatedAt
) {
    public SalesReportResponse(UUID storeId, UUID registerId, UUID cashierId, UUID categoryId, UUID productId,
            LocalDate dateFrom, LocalDate dateTo, BigDecimal grossSales, BigDecimal netSales, BigDecimal discounts,
            BigDecimal refunds, BigDecimal taxes, BigDecimal payments, long saleCount, long refundCount,
            List<SalesReportPaymentBreakdown> paymentBreakdown, Instant generatedAt) {
        this(storeId, registerId, cashierId, categoryId, productId, dateFrom, dateTo, grossSales, netSales,
                discounts, refunds, taxes, payments, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                saleCount, refundCount, paymentBreakdown, generatedAt);
    }
}
