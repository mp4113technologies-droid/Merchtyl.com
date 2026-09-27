package com.merchtyl.reports;

import com.merchtyl.common.PageResponse;
import com.merchtyl.registersession.RegisterSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterReportResponse(
        UUID storeId,
        UUID registerId,
        UUID cashierId,
        RegisterSessionStatus status,
        LocalDate dateFrom,
        LocalDate dateTo,
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
        long sessionCount,
        long closedSessionCount,
        long openSessionCount,
        PageResponse<RegisterReportRow> rows,
        Instant generatedAt
) {
    public RegisterReportResponse(UUID storeId, UUID registerId, UUID cashierId, RegisterSessionStatus status,
            LocalDate dateFrom, LocalDate dateTo, BigDecimal openingCash, BigDecimal retailCash,
            BigDecimal retailCashReceived, BigDecimal retailChange, BigDecimal lotteryCash,
            BigDecimal lotteryCashSales, BigDecimal lotteryPayouts, BigDecimal payoutReversals,
            BigDecimal lotterySaleCancellations, BigDecimal refunds, BigDecimal cashMovements,
            BigDecimal cashMovementIn, BigDecimal cashMovementOut, BigDecimal cashOut,
            BigDecimal expectedCash, BigDecimal countedCash, BigDecimal variance, BigDecimal taxableSales,
            BigDecimal nonTaxableSales, BigDecimal taxCollected, BigDecimal merchandiseNetSales,
            long sessionCount, long closedSessionCount, long openSessionCount,
            PageResponse<RegisterReportRow> rows, Instant generatedAt) {
        this(storeId, registerId, cashierId, status, dateFrom, dateTo, openingCash, retailCash,
                retailCashReceived, retailChange, lotteryCash, lotteryCashSales, lotteryPayouts,
                payoutReversals, lotterySaleCancellations, refunds, cashMovements, cashMovementIn,
                cashMovementOut, cashOut, expectedCash, countedCash, variance, taxableSales,
                nonTaxableSales, taxCollected, BigDecimal.ZERO.setScale(2), taxCollected,
                merchandiseNetSales, sessionCount, closedSessionCount, openSessionCount, rows, generatedAt);
    }

    public BigDecimal taxCollected() { return totalTaxCollected; }
}
