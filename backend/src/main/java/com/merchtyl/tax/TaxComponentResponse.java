package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;

public record TaxComponentResponse(
        UUID id,
        UUID taxTypeId,
        UUID taxJurisdictionId,
        String code,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static TaxComponentResponse from(TaxComponent component) {
        return new TaxComponentResponse(
                component.getId(),
                component.getTaxType().getId(),
                component.getTaxJurisdiction().getId(),
                component.getCode(),
                component.getName(),
                component.getDescription(),
                component.isActive(),
                component.getCreatedAt(),
                component.getUpdatedAt(),
                component.getVersion());
    }
}
