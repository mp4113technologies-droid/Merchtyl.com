package com.merchtyl.tax;

import java.util.UUID;

public record TaxComponentSearchRequest(
        UUID taxTypeId,
        UUID taxJurisdictionId,
        String code,
        String name,
        Boolean active,
        int page,
        int size
) {
}
