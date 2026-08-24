package com.merchtyl.tax;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TaxJurisdictionRequest(
        @NotNull UUID countryId,
        UUID administrativeAreaId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 180) String name,
        @NotNull TaxJurisdictionType type,
        boolean active
) {
}
