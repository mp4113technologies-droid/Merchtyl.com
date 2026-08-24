package com.merchtyl.registersession;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RegisterSessionCloseRequest(
        @NotNull BigDecimal countedCash,
        @NotNull Long version
) {
}
