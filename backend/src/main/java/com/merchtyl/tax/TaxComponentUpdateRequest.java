package com.merchtyl.tax;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TaxComponentUpdateRequest(
        @NotNull UUID taxTypeId,
        @NotNull UUID taxJurisdictionId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 1000) String description,
        boolean active,
        @NotNull Long version
) {
}
