package com.merchtyl.registersession;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record RegisterSessionForceCloseRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal countedCash,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal retainedCash,
        String overrideReason,
        @NotNull String reason,
        String varianceExplanation,
        @NotNull Long version
) {
    public RegisterSessionForceCloseRequest(BigDecimal countedCash, String reason, Long version) {
        this(countedCash, null, null, reason, null, version);
    }
}
