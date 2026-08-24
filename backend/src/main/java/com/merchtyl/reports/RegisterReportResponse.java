package com.merchtyl.reports;

import com.merchtyl.registersession.RegisterSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        long sessionCount,
        long closedSessionCount,
        List<RegisterReportRow> rows,
        Instant generatedAt
) {
}
