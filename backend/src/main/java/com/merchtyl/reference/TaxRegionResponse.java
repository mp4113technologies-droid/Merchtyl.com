package com.merchtyl.reference;

import java.util.UUID;

public record TaxRegionResponse(
        UUID id,
        String countryCode,
        UUID administrativeDivisionId,
        String administrativeDivisionCode,
        String code,
        String name,
        boolean active,
        boolean defaultForDivision,
        UUID taxJurisdictionId
) {
    static TaxRegionResponse from(TaxRegion region) {
        return new TaxRegionResponse(
                region.getId(),
                region.getCountry().getCode(),
                region.getAdministrativeDivision() == null ? null : region.getAdministrativeDivision().getId(),
                region.getAdministrativeDivision() == null ? null : region.getAdministrativeDivision().getCode(),
                region.getCode(),
                region.getName(),
                region.isActive(),
                region.isDefaultForDivision(),
                region.getTaxJurisdiction() == null ? null : region.getTaxJurisdiction().getId());
    }
}
