package com.merchtyl.registersession;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record RegisterSessionCloseRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal countedCash,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal retainedCash,
        String overrideReason,
        String varianceExplanation,
        @NotNull Long version
) {
    public RegisterSessionCloseRequest(BigDecimal countedCash, Long version) {
        this(countedCash, null, null, null, version);
    }
}
