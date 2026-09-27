package com.merchtyl.reports;

import com.merchtyl.registersession.RegisterSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterReportRow(
        UUID registerSessionId,
        UUID storeId,
        String storeCode,
        String storeName,
        UUID registerId,
        String registerCode,
        String registerName,
        UUID cashierId,
        String cashierEmail,
        String cashierDisplayName,
        RegisterSessionStatus status,
        String currencyCode,
        LocalDate businessDate,
        BigDecimal openingCash,
        BigDecimal retailCash,
        BigDecimal retailCashReceived,
        BigDecimal retailChange,
        BigDecimal lotteryCash,
        BigDecimal lotteryCashSales,
        BigDecimal lotteryPayouts,
        BigDecimal payoutReversals,
        BigDecimal lotterySaleCancellations,
        BigDecimal refunds,
        BigDecimal cashMovements,
        BigDecimal cashMovementIn,
        BigDecimal cashMovementOut,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        BigDecimal taxableSales,
        BigDecimal nonTaxableSales,
        BigDecimal generalTaxCollected,
        BigDecimal vapeTaxCollected,
        BigDecimal totalTaxCollected,
        BigDecimal merchandiseNetSales,
        Instant openedAt,
        Instant closedAt
) {
    public BigDecimal taxCollected() { return totalTaxCollected; }
}
