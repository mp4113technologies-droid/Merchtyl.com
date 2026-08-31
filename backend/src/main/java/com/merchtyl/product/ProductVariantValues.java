package com.merchtyl.product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantValues(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal cost,
        BigDecimal price,
        boolean active
) {
    public ProductVariantValues(String sku, String name, String description, BigDecimal cost, BigDecimal price, boolean active) {
        this(null, sku, name, description, cost, price, active);
    }
}
