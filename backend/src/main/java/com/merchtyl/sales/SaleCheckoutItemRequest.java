package com.merchtyl.sales;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleCheckoutItemRequest(
        UUID productId,
        UUID variantId,
        UUID foodMenuItemId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
        Boolean ageVerified
) {
    @AssertTrue(message = "checkout item must identify a product or food menu item")
    public boolean isItemReferencePresent() {
        return productId != null || foodMenuItemId != null;
    }
}
