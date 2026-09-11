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
        boolean active,
        boolean depositEnabled,
        DepositType depositType,
        BigDecimal depositAmount
) {
    public ProductVariantValues(UUID id, String sku, String name, String description, BigDecimal cost, BigDecimal price, boolean active) {
        this(id, sku, name, description, cost, price, active, false, null, null);
    }
    public ProductVariantValues(String sku, String name, String description, BigDecimal cost, BigDecimal price, boolean active) {
        this(null, sku, name, description, cost, price, active, false, null, null);
    }
}
