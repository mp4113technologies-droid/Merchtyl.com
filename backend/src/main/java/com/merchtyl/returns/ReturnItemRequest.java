package com.merchtyl.returns;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnItemRequest(
        @NotNull UUID originalSaleItemId,
        @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 8, fraction = 4) BigDecimal quantity,
        @Size(max = 1000) String reason
) {
}
