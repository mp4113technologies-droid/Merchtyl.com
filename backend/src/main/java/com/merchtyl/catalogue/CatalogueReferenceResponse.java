package com.merchtyl.catalogue;

import java.time.Instant;
import java.util.UUID;

public record CatalogueReferenceResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static CatalogueReferenceResponse from(CatalogueReference reference) {
        return new CatalogueReferenceResponse(
                reference.getId(),
                reference.getCode(),
                reference.getName(),
                reference.getDescription(),
                reference.isActive(),
                reference.getCreatedAt(),
                reference.getUpdatedAt(),
                reference.getVersion());
    }
}
