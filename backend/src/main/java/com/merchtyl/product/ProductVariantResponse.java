package com.merchtyl.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal cost,
        BigDecimal price,
        boolean active,
        boolean depositEnabled,
        DepositType depositType,
        BigDecimal depositAmount,
        List<ProductBarcodeResponse> barcodes,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static ProductVariantResponse from(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getDescription(),
                variant.getCost(),
                variant.getPrice(),
                variant.isActive(),
                variant.isDepositEnabled(),
                variant.getDepositType(),
                variant.getDepositAmount(),
                variant.getProduct().getBarcodes().stream()
                        .filter(barcode -> barcode.getVariant() != null && variant.getId().equals(barcode.getVariant().getId()))
                        .map(ProductBarcodeResponse::from)
                        .toList(),
                variant.getCreatedAt(),
                variant.getUpdatedAt(),
                variant.getVersion());
    }
}
