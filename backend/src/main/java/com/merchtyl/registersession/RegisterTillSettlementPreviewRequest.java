package com.merchtyl.registersession;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RegisterTillSettlementPreviewRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal countedCash,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal retainedCash,
        String overrideReason,
        @NotNull Long version
) {}
