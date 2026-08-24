package com.merchtyl.tax;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaxRateRequest(
        @NotNull UUID taxComponentId,
        @NotNull @DecimalMin("0.000000") BigDecimal percentageRate,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean includedInPrice,
        boolean compoundOnPreviousTax,
        @PositiveOrZero int calculationOrder,
        @NotNull TaxRateStatus status,
        @Size(max = 180) String source,
        @Size(max = 500) String sourceReference,
        @Size(max = 180) String verifiedBy,
        Instant verifiedAt
) {
}
