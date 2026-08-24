package com.merchtyl.tax;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaxRateResponse(
        UUID id,
        UUID taxComponentId,
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
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static TaxRateResponse from(TaxRate rate) {
        return new TaxRateResponse(
                rate.getId(),
                rate.getTaxComponent().getId(),
                rate.getPercentageRate(),
                rate.getEffectiveFrom(),
                rate.getEffectiveTo(),
                rate.isIncludedInPrice(),
                rate.isCompoundOnPreviousTax(),
                rate.getCalculationOrder(),
                rate.getStatus(),
                rate.getSource(),
                rate.getSourceReference(),
                rate.getVerifiedBy(),
                rate.getVerifiedAt(),
                rate.getCreatedAt(),
                rate.getUpdatedAt(),
                rate.getVersion());
    }
}
