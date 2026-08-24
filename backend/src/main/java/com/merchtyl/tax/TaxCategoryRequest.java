package com.merchtyl.tax;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TaxCategoryRequest(
        UUID taxGroupId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 180) String name,
        @NotNull TaxTreatment treatment,
        @Size(max = 1000) String description,
        boolean active
) {
}
