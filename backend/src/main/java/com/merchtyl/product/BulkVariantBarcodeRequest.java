package com.merchtyl.product;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkVariantBarcodeRequest(
        @NotEmpty @Size(max = 500) List<@jakarta.validation.constraints.NotBlank @Size(max = 128) String> barcodes
) {}
