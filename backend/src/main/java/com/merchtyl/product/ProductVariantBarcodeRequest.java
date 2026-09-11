package com.merchtyl.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProductVariantBarcodeRequest(
        UUID id,
        @NotBlank @Size(max = 128) String barcode,
        boolean primaryBarcode,
        boolean active
) {
    public ProductVariantBarcodeRequest(String barcode) {
        this(null, barcode, false, true);
    }
}
