package com.merchtyl.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductVariantRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.0000") BigDecimal cost,
        @NotNull @DecimalMin("0.0000") BigDecimal price,
        boolean active
) {
}
