package com.merchtyl.reference;

import com.merchtyl.tax.AdministrativeArea;

import java.util.UUID;

public record AdministrativeDivisionResponse(
        UUID id,
        String countryCode,
        String code,
        String name,
        String divisionType,
        String defaultTimezone,
        String defaultTaxRegionCode,
        boolean active,
        int displayOrder
) {
    static AdministrativeDivisionResponse from(AdministrativeArea division) {
        return new AdministrativeDivisionResponse(
                division.getId(),
                division.getCountry().getCode(),
                division.getCode(),
                division.getName(),
                division.getType().name(),
                division.getDefaultTimezone() == null ? null : division.getDefaultTimezone().getIanaName(),
                division.getDefaultTaxRegion() == null ? null : division.getDefaultTaxRegion().getCode(),
                division.isActive(),
                division.getDisplayOrder());
    }
}
