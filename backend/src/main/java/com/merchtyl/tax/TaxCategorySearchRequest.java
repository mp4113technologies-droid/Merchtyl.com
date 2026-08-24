package com.merchtyl.tax;

import java.util.UUID;

public record TaxCategorySearchRequest(
        UUID taxGroupId,
        String code,
        String name,
        TaxTreatment treatment,
        Boolean active,
        int page,
        int size
) {
}
