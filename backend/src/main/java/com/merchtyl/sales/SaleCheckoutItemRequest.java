package com.merchtyl.sales;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleCheckoutItemRequest(
        @NotNull UUID productId,
        UUID variantId,
        UUID foodMenuItemId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
        Boolean ageVerified
) {}
