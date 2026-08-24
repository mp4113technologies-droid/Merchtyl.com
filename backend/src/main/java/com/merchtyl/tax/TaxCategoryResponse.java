package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;

public record TaxCategoryResponse(
        UUID id,
        UUID taxGroupId,
        String code,
        String name,
        TaxTreatment treatment,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static TaxCategoryResponse from(TaxCategory category) {
        return new TaxCategoryResponse(
                category.getId(),
                category.getTaxGroup() == null ? null : category.getTaxGroup().getId(),
                category.getCode(),
                category.getName(),
                category.getTreatment(),
                category.getDescription(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getVersion());
    }
}
