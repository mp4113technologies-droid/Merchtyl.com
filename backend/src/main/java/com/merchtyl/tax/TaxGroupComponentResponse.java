package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;

public record TaxGroupComponentResponse(
        UUID id,
        UUID taxGroupId,
        UUID taxComponentId,
        int calculationOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static TaxGroupComponentResponse from(TaxGroupComponent component) {
        return new TaxGroupComponentResponse(
                component.getId(),
                component.getTaxGroup().getId(),
                component.getTaxComponent().getId(),
                component.getCalculationOrder(),
                component.isActive(),
                component.getCreatedAt(),
                component.getUpdatedAt(),
                component.getVersion());
    }
}
