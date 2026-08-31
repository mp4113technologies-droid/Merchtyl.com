package com.merchtyl.product;

import java.util.UUID;

public record ProductBarcodeValues(
        UUID id,
        String barcode,
        UUID variantId,
        String variantSku,
        boolean primaryBarcode,
        boolean active
) {
    public ProductBarcodeValues(String barcode, String variantSku, boolean primaryBarcode, boolean active) {
        this(null, barcode, null, variantSku, primaryBarcode, active);
    }
}
