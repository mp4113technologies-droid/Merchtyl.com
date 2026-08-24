package com.merchtyl.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProductUpdateRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 1000) String description,
        @NotNull SellableType sellableType,
        UUID unitOfMeasureId,
        @NotNull @DecimalMin("0.0000") BigDecimal cost,
        @NotNull @DecimalMin("0.0000") BigDecimal price,
        UUID categoryId,
        UUID brandId,
        boolean active,
        boolean inventoryTrackingEnabled,
        boolean decimalQuantityAllowed,
        @Size(max = 1000) String imageUrl,
        UUID taxCategoryId,
        @Valid List<ProductVariantRequest> variants,
        @Valid List<ProductBarcodeRequest> barcodes,
        Set<ProductCapability> capabilities,
        @NotNull Long version,
        @Min(1) @Max(99) Integer minimumAge
) {
    public ProductUpdateRequest(String sku, String name, String description, SellableType sellableType, UUID unitOfMeasureId,
                                BigDecimal cost, BigDecimal price, UUID categoryId, UUID brandId, boolean active,
                                boolean inventoryTrackingEnabled, boolean decimalQuantityAllowed, String imageUrl,
                                UUID taxCategoryId, List<ProductVariantRequest> variants, List<ProductBarcodeRequest> barcodes,
                                Set<ProductCapability> capabilities, Long version) {
        this(sku, name, description, sellableType, unitOfMeasureId, cost, price, categoryId, brandId, active,
                inventoryTrackingEnabled, decimalQuantityAllowed, imageUrl, taxCategoryId, variants, barcodes,
                capabilities, version, null);
    }
}
