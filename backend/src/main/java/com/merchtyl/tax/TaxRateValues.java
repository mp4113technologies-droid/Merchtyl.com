package com.merchtyl.tax;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record TaxRateValues(
        TaxComponent taxComponent,
        BigDecimal percentageRate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean includedInPrice,
        boolean compoundOnPreviousTax,
        int calculationOrder,
        TaxRateStatus status,
        String source,
        String sourceReference,
        String verifiedBy,
        Instant verifiedAt
) {
}
