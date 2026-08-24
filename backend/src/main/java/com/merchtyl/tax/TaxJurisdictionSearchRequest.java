package com.merchtyl.tax;

import java.util.UUID;

public record TaxJurisdictionSearchRequest(
        UUID countryId,
        UUID administrativeAreaId,
        String code,
        String name,
        TaxJurisdictionType type,
        Boolean active,
        int page,
        int size
) {
}
