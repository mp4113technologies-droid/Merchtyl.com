package com.merchtyl.product;

import java.util.UUID;

public record BarcodeOwnershipResponse(
        String barcode,
        boolean assigned,
        UUID assignmentId,
        Long assignmentVersion,
        UUID productId,
        String productName,
        UUID variantId,
        String variantName,
        boolean productActive
) {
    static BarcodeOwnershipResponse unassigned(String barcode) {
        return new BarcodeOwnershipResponse(barcode, false, null, null, null, null, null, null, false);
    }
}
