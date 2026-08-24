package com.merchtyl.product;

import java.math.BigDecimal;

public record ProductVariantValues(
        String sku,
        String name,
        String description,
        BigDecimal cost,
        BigDecimal price,
        boolean active
) {
}
