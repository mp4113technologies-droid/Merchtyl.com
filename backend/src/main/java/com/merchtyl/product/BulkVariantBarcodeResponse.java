package com.merchtyl.product;

import java.util.List;
import java.util.UUID;

public record BulkVariantBarcodeResponse(UUID variantId, int addedCount, List<ProductBarcodeResponse> barcodes) {}
