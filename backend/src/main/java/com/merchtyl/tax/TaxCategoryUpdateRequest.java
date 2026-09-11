package com.merchtyl.tax;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.math.BigDecimal;

public record TaxCategoryUpdateRequest(
        UUID taxGroupId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 180) String name,
        @NotNull TaxTreatment treatment,
        @Size(max = 1000) String description,
        boolean active,
        @NotNull Long version,
        TaxCategoryType categoryType,
        BigDecimal percentageRate
) {
    public TaxCategoryUpdateRequest(UUID taxGroupId, String code, String name, TaxTreatment treatment, String description, boolean active, Long version) {
        this(taxGroupId, code, name, treatment, description, active, version, TaxCategoryType.STANDARD, null);
    }
}
