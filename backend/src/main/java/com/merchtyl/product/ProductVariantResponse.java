package com.merchtyl.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal cost,
        BigDecimal price,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static ProductVariantResponse from(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getDescription(),
                variant.getCost(),
                variant.getPrice(),
                variant.isActive(),
                variant.getCreatedAt(),
                variant.getUpdatedAt(),
                variant.getVersion());
    }
}
