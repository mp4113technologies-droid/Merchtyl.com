package com.merchtyl.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProductVariantBarcodeRequest(
        UUID id,
        @NotBlank @Size(max = 128) String barcode,
        boolean primaryBarcode,
        boolean active,
        UUID reassignFromBarcodeId,
        Long reassignFromVersion
) {
    public ProductVariantBarcodeRequest(String barcode) {
        this(null, barcode, false, true, null, null);
    }

    public ProductVariantBarcodeRequest(UUID id, String barcode, boolean primaryBarcode, boolean active) {
        this(id, barcode, primaryBarcode, active, null, null);
    }
}
