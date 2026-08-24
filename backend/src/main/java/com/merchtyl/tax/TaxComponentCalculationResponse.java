package com.merchtyl.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxComponentCalculationResponse(
        UUID taxComponentId,
        String taxComponentCode,
        String taxComponentName,
        UUID taxRateId,
        BigDecimal percentageRate,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        boolean includedInPrice,
        boolean compoundOnPreviousTax,
        int calculationOrder,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String explanation) {
}
