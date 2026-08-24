package com.merchtyl.registersession;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RegisterSessionForceCloseRequest(
        @NotNull BigDecimal countedCash,
        @NotNull String reason,
        @NotNull Long version
) {
}
