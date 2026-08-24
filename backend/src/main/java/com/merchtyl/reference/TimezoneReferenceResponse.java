package com.merchtyl.reference;

import java.util.UUID;

public record TimezoneReferenceResponse(
        UUID id,
        String ianaName,
        String displayName,
        String countryCode,
        boolean active,
        int displayOrder,
        boolean defaultForDivision
) {
    static TimezoneReferenceResponse from(TimezoneReference timezone) {
        return from(timezone, false);
    }

    static TimezoneReferenceResponse from(TimezoneReference timezone, boolean defaultForDivision) {
        return new TimezoneReferenceResponse(
                timezone.getId(),
                timezone.getIanaName(),
                timezone.getDisplayName(),
                timezone.getCountry() == null ? null : timezone.getCountry().getCode(),
                timezone.isActive(),
                timezone.getDisplayOrder(),
                defaultForDivision);
    }
}
