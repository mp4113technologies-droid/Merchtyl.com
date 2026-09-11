package com.merchtyl.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductVariantRequest(
        UUID id,
        @Size(max = 64) String sku,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.0000") BigDecimal cost,
        @NotNull @DecimalMin("0.0000") BigDecimal price,
        boolean active,
        boolean depositEnabled,
        DepositType depositType,
        @DecimalMin(value = "0.0000", inclusive = false, message = "Enter a valid deposit amount.") BigDecimal depositAmount,
        @Valid List<ProductVariantBarcodeRequest> barcodes
) {
    public ProductVariantRequest(String sku, String name, String description, BigDecimal cost, BigDecimal price, boolean active) {
        this(null, sku, name, description, cost, price, active, false, null, null, List.of());
    }

    public ProductVariantRequest(String sku, String name, String description, BigDecimal cost, BigDecimal price,
                                 boolean active, List<ProductVariantBarcodeRequest> barcodes) {
        this(null, sku, name, description, cost, price, active, false, null, null, barcodes);
    }

    public ProductVariantRequest(UUID id, String sku, String name, String description, BigDecimal cost,
                                 BigDecimal price, boolean active, List<ProductVariantBarcodeRequest> barcodes) {
        this(id, sku, name, description, cost, price, active, false, null, null, barcodes);
    }
}
