package com.merchtyl.catalogue;

import java.time.Instant;
import java.util.UUID;

public record CatalogueReferenceResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        boolean systemManaged,
        String systemType,
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
                reference instanceof Category category && category.isSystemManaged(),
                reference instanceof Category category ? category.getSystemType() : null,
                reference.getCreatedAt(),
                reference.getUpdatedAt(),
                reference.getVersion());
    }
}
