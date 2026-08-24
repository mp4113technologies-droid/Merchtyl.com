package com.merchtyl.supplier;

import java.time.Instant;
import java.util.UUID;

public record ProductSupplierResponse(
        UUID id,
        UUID productId,
        UUID supplierId,
        String supplierSku,
        boolean preferred,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static ProductSupplierResponse from(ProductSupplier productSupplier) {
        return new ProductSupplierResponse(
                productSupplier.getId(),
                productSupplier.getProductId(),
                productSupplier.getSupplier().getId(),
                productSupplier.getSupplierSku(),
                productSupplier.isPreferred(),
                productSupplier.isActive(),
                productSupplier.getCreatedAt(),
                productSupplier.getUpdatedAt(),
                productSupplier.getVersion());
    }
}
