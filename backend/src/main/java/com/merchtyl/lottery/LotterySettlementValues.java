package com.merchtyl.lottery;

import com.merchtyl.store.Store;
import com.merchtyl.tax.TaxJurisdiction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record LotterySettlementValues(
        LotteryOperator operator,
        TaxJurisdiction jurisdiction,
        Store store,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal grossSales,
        BigDecimal totalPayouts,
        BigDecimal cancellations,
        BigDecimal adjustments,
        BigDecimal commission,
        BigDecimal expectedSettlement,
        String currencyCode,
        Instant calculatedAt
) {
}
