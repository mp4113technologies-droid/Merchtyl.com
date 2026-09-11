package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

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
        long version,
        UUID tenantId,
        TaxCategoryType categoryType,
        BigDecimal percentageRate,
        boolean systemManaged
) {
    public TaxCategoryResponse(UUID id, UUID taxGroupId, String code, String name, TaxTreatment treatment,
                               String description, boolean active, Instant createdAt, Instant updatedAt, long version) {
        this(id, taxGroupId, code, name, treatment, description, active, createdAt, updatedAt, version,
                null, TaxCategoryType.STANDARD, null, true);
    }

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
                category.getVersion(),
                category.getTenantId(),
                category.getCategoryType(),
                category.getPercentageRate(),
                category.isSystemManaged());
    }
}
