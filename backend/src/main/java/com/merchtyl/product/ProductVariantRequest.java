package com.merchtyl.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantRequest(
        UUID id,
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.0000") BigDecimal cost,
        @NotNull @DecimalMin("0.0000") BigDecimal price,
        boolean active
) {
    public ProductVariantRequest(String sku, String name, String description, BigDecimal cost, BigDecimal price, boolean active) {
        this(null, sku, name, description, cost, price, active);
    }
}
