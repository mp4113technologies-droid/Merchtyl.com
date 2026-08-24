package com.merchtyl.eod;

import java.math.BigDecimal;

public record RegisterSummaryValues(
        BigDecimal openingFloat,
        BigDecimal cashReceipts,
        BigDecimal changeGiven,
        BigDecimal cashRefunds,
        BigDecimal lotteryCashSales,
        BigDecimal lotteryPayouts,
        BigDecimal lotteryPayoutReversals,
        BigDecimal lotterySaleCancellations,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal safeDrops,
        BigDecimal floatAdditions,
        BigDecimal floatRemovals,
        BigDecimal expenses,
        BigDecimal closingAdjustments,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance
) {
}
