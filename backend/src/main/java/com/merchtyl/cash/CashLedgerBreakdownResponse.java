package com.merchtyl.cash;

import java.math.BigDecimal;
import java.util.List;

public record CashLedgerBreakdownResponse(
        BigDecimal openingCash,
        BigDecimal retailCashReceived,
        BigDecimal retailChange,
        BigDecimal retailRefunds,
        BigDecimal depositPayouts,
        BigDecimal lotteryCashSales,
        BigDecimal lotteryPayouts,
        BigDecimal payoutReversals,
        BigDecimal lotterySaleCancellations,
        BigDecimal otherCashIn,
        BigDecimal otherCashOut,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal expectedCash,
        List<CashLedgerSourceBreakdownResponse> sourceBreakdown
) {
    public CashLedgerBreakdownResponse(BigDecimal openingCash, BigDecimal retailCashReceived,
            BigDecimal retailChange, BigDecimal retailRefunds, BigDecimal lotteryCashSales,
            BigDecimal lotteryPayouts, BigDecimal payoutReversals, BigDecimal lotterySaleCancellations,
            BigDecimal otherCashIn, BigDecimal otherCashOut, BigDecimal totalIn, BigDecimal totalOut,
            BigDecimal expectedCash, List<CashLedgerSourceBreakdownResponse> sourceBreakdown) {
        this(openingCash, retailCashReceived, retailChange, retailRefunds, BigDecimal.ZERO.setScale(2),
                lotteryCashSales, lotteryPayouts, payoutReversals, lotterySaleCancellations, otherCashIn,
                otherCashOut, totalIn, totalOut, expectedCash, sourceBreakdown);
    }
}
