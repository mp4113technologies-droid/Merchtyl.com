package com.merchtyl.register;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record RegisterUpdateRequest(
        @NotNull UUID storeId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 1000) String locationDescription,
        boolean active,
        @NotNull RegisterType type,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal tillFloatOverride,
        @NotNull Long version
) {
}
