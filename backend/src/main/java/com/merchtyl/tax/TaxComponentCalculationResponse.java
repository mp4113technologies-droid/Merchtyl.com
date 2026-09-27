package com.merchtyl.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxComponentCalculationResponse(
        UUID taxComponentId,
        String taxComponentCode,
        String taxComponentName,
        TaxReportingType reportingType,
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
    public TaxComponentCalculationResponse(UUID taxComponentId, String taxComponentCode, String taxComponentName,
            UUID taxRateId, BigDecimal percentageRate, BigDecimal taxableAmount, BigDecimal taxAmount,
            boolean includedInPrice, boolean compoundOnPreviousTax, int calculationOrder,
            LocalDate effectiveFrom, LocalDate effectiveTo, String explanation) {
        this(taxComponentId, taxComponentCode, taxComponentName, TaxReportingType.GENERAL_SALES_TAX,
                taxRateId, percentageRate, taxableAmount, taxAmount, includedInPrice, compoundOnPreviousTax,
                calculationOrder, effectiveFrom, effectiveTo, explanation);
    }
}
