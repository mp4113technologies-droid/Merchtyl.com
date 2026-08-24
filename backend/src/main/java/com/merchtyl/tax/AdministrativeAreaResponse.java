package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;

public record AdministrativeAreaResponse(
        UUID id,
        UUID countryId,
        String code,
        String name,
        AdministrativeAreaType type,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static AdministrativeAreaResponse from(AdministrativeArea area) {
        return new AdministrativeAreaResponse(
                area.getId(),
                area.getCountry().getId(),
                area.getCode(),
                area.getName(),
                area.getType(),
                area.isActive(),
                area.getCreatedAt(),
                area.getUpdatedAt(),
                area.getVersion());
    }
}
