package com.merchtyl.sales;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleCheckoutItemRequest(
        SaleLineType lineType,
        UUID productId,
        UUID variantId,
        UUID foodMenuItemId,
        @Size(max = 180) String description,
        BigDecimal unitPrice,
        CustomItemTaxTreatment taxTreatment,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
        Boolean ageVerified
) {
    public SaleCheckoutItemRequest(UUID productId, UUID variantId, UUID foodMenuItemId,
                                   BigDecimal quantity, Boolean ageVerified) {
        this(SaleLineType.CATALOG_PRODUCT, productId, variantId, foodMenuItemId, null, null, null,
                quantity, ageVerified);
    }

    public SaleLineType resolvedLineType() {
        return lineType == null ? SaleLineType.CATALOG_PRODUCT : lineType;
    }

    @AssertTrue(message = "INVALID_CHECKOUT_ITEM")
    public boolean isValidShape() {
        if (resolvedLineType() == SaleLineType.CUSTOM_ITEM) {
            return productId == null && variantId == null && foodMenuItemId == null;
        }
        return productId != null || foodMenuItemId != null;
    }
}
