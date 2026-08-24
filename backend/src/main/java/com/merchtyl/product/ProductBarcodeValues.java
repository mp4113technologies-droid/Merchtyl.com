package com.merchtyl.product;

public record ProductBarcodeValues(
        String barcode,
        String variantSku,
        boolean primaryBarcode,
        boolean active
) {
}
