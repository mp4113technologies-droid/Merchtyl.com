package com.merchtyl.tax;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AdministrativeAreaRequest(
        @NotNull UUID countryId,
        @NotBlank @Size(max = 16) String code,
        @NotBlank @Size(max = 180) String name,
        @NotNull AdministrativeAreaType type,
        boolean active
) {
}
