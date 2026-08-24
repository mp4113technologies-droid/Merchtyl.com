package com.merchtyl.lottery;

import com.merchtyl.tax.TaxJurisdiction;

record LotteryOperatorValues(
        String code,
        String name,
        TaxJurisdiction jurisdiction,
        String supportContact,
        SettlementFrequency settlementFrequency,
        boolean active
) {
}
