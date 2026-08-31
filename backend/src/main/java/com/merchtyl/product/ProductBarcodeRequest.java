package com.merchtyl.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ProductBarcodeRequest(
        UUID id,
        @NotBlank @Size(max = 128) String barcode,
        UUID variantId,
        @Size(max = 64) String variantSku,
        boolean primaryBarcode,
        boolean active
) {
    public ProductBarcodeRequest(String barcode, String variantSku, boolean primaryBarcode, boolean active) {
        this(null, barcode, null, variantSku, primaryBarcode, active);
    }
}
