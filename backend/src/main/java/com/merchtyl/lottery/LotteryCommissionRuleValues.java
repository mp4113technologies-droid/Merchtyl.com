package com.merchtyl.lottery;

import com.merchtyl.store.Store;
import com.merchtyl.tax.TaxJurisdiction;

import java.math.BigDecimal;
import java.time.LocalDate;

record LotteryCommissionRuleValues(
        String name,
        LotteryOperator operator,
        TaxJurisdiction jurisdiction,
        Store store,
        LotteryCommissionRuleType ruleType,
        BigDecimal commissionRatePercent,
        BigDecimal fixedAmount,
        String currencyCode,
        LotteryCommissionPeriod fixedPeriod,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LotteryCommissionRuleStatus status,
        String notes
) {
}
