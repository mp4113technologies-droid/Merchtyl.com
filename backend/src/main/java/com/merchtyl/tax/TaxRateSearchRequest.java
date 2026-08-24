package com.merchtyl.tax;

import java.util.UUID;

public record TaxRateSearchRequest(
        UUID taxComponentId,
        TaxRateStatus status,
        Boolean includedInPrice,
        Boolean compoundOnPreviousTax,
        Integer calculationOrder,
        int page,
        int size
) {
}
