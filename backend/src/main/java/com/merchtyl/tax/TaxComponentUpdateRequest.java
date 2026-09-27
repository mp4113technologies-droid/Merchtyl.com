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
        TaxReportingType reportingType,
        boolean active,
        @NotNull Long version
) {
    public TaxComponentUpdateRequest(UUID taxTypeId, UUID taxJurisdictionId, String code, String name, String description, boolean active, Long version) {
        this(taxTypeId, taxJurisdictionId, code, name, description, TaxReportingType.GENERAL_SALES_TAX, active, version);
    }
}
