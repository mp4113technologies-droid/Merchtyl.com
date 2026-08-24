package com.merchtyl.tax;

import java.time.LocalDate;

public record TaxRuleSearchRequest(
        String code,
        String name,
        Boolean active,
        LocalDate effectiveOn,
        int page,
        int size) {
}
