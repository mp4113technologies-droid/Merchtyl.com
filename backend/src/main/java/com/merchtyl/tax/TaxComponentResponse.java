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
        TaxReportingType reportingType,
        boolean systemManaged,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public TaxComponentResponse(UUID id, UUID taxTypeId, UUID taxJurisdictionId, String code, String name, String description, boolean active, Instant createdAt, Instant updatedAt, long version) {
        this(id, taxTypeId, taxJurisdictionId, code, name, description, TaxReportingType.GENERAL_SALES_TAX, false, active, createdAt, updatedAt, version);
    }
    static TaxComponentResponse from(TaxComponent component) {
        return new TaxComponentResponse(
                component.getId(),
                component.getTaxType().getId(),
                component.getTaxJurisdiction().getId(),
                component.getCode(),
                component.getName(),
                component.getDescription(),
                component.getReportingType(),
                component.isSystemManaged(),
                component.isActive(),
                component.getCreatedAt(),
                component.getUpdatedAt(),
                component.getVersion());
    }
}
