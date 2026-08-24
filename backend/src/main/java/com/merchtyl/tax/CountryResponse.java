package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;

public record CountryResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static CountryResponse from(Country country) {
        return new CountryResponse(
                country.getId(),
                country.getCode(),
                country.getName(),
                country.isActive(),
                country.getCreatedAt(),
                country.getUpdatedAt(),
                country.getVersion());
    }
}
