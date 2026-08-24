package com.merchtyl.product;

import java.time.Instant;
import java.util.UUID;

public record ProductBarcodeResponse(
        UUID id,
        String barcode,
        UUID variantId,
        String variantSku,
        boolean primaryBarcode,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static ProductBarcodeResponse from(ProductBarcode barcode) {
        ProductVariant variant = barcode.getVariant();
        return new ProductBarcodeResponse(
                barcode.getId(),
                barcode.getBarcode(),
                variant == null ? null : variant.getId(),
                variant == null ? null : variant.getSku(),
                barcode.isPrimaryBarcode(),
                barcode.isActive(),
                barcode.getCreatedAt(),
                barcode.getUpdatedAt(),
                barcode.getVersion());
    }
}
