package com.merchtyl.registersession;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record RegisterSessionOpenRequest(
        @NotNull UUID storeId,
        @NotNull UUID registerId,
        @Schema(description = "Optional when register device enforcement is disabled; required when it is enabled.") UUID deviceId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal openingCash
) {
}
