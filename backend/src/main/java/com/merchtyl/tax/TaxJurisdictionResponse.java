package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;

public record TaxJurisdictionResponse(
        UUID id,
        UUID countryId,
        UUID administrativeAreaId,
        String code,
        String name,
        TaxJurisdictionType type,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static TaxJurisdictionResponse from(TaxJurisdiction jurisdiction) {
        return new TaxJurisdictionResponse(
                jurisdiction.getId(),
                jurisdiction.getCountry().getId(),
                jurisdiction.getAdministrativeArea() == null ? null : jurisdiction.getAdministrativeArea().getId(),
                jurisdiction.getCode(),
                jurisdiction.getName(),
                jurisdiction.getType(),
                jurisdiction.isActive(),
                jurisdiction.getCreatedAt(),
                jurisdiction.getUpdatedAt(),
                jurisdiction.getVersion());
    }
}
