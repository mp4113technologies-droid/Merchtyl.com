package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;

public record TaxTypeResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static TaxTypeResponse from(TaxType taxType) {
        return new TaxTypeResponse(
                taxType.getId(),
                taxType.getCode(),
                taxType.getName(),
                taxType.getDescription(),
                taxType.isActive(),
                taxType.getCreatedAt(),
                taxType.getUpdatedAt(),
                taxType.getVersion());
    }
}
