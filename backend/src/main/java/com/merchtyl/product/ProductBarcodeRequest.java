package com.merchtyl.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductBarcodeRequest(
        @NotBlank @Size(max = 128) String barcode,
        @Size(max = 64) String variantSku,
        boolean primaryBarcode,
        boolean active
) {
}
