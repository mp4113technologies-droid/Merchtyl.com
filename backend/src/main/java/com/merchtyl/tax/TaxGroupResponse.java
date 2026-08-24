package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;

public record TaxGroupResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static TaxGroupResponse from(TaxGroup group) {
        return new TaxGroupResponse(
                group.getId(),
                group.getCode(),
                group.getName(),
                group.getDescription(),
                group.isActive(),
                group.getCreatedAt(),
                group.getUpdatedAt(),
                group.getVersion());
    }
}
