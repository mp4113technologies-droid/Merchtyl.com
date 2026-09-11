package com.merchtyl.tax;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.math.BigDecimal;

public record TaxCategoryRequest(
        UUID taxGroupId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 180) String name,
        @NotNull TaxTreatment treatment,
        @Size(max = 1000) String description,
        boolean active,
        TaxCategoryType categoryType,
        BigDecimal percentageRate
) {
    public TaxCategoryRequest(UUID taxGroupId, String code, String name, TaxTreatment treatment, String description, boolean active) {
        this(taxGroupId, code, name, treatment, description, active, TaxCategoryType.STANDARD, null);
    }
}
