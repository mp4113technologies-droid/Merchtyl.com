package com.merchtyl.eod;

import java.math.BigDecimal;

public record EndOfDayReportTotals(
        BigDecimal grossSales,
        BigDecimal netSales,
        BigDecimal discountTotal,
        BigDecimal refundTotal,
        BigDecimal voidTotal,
        BigDecimal taxTotal,
        long transactionCount,
        BigDecimal averageTransactionValue,
        BigDecimal highestTransactionValue,
        BigDecimal lowestTransactionValue,
        BigDecimal itemsSold,
        BigDecimal averageBasketSize,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal cashVariance,
        String currencyCode
) {
}
