package com.merchtyl.product;

import java.math.BigDecimal;
import java.util.UUID;

public record PosBarcodeLookupResponse(
        UUID productId,
        UUID variantId,
        String productName,
        String variantName,
        String barcode,
        String sku,
        UUID unitOfMeasureId,
        BigDecimal price,
        UUID taxCategoryId,
        String taxCategoryName,
        BigDecimal availableQuantity,
        boolean active,
        boolean ageRestricted,
        Integer minimumAge
) {
}
