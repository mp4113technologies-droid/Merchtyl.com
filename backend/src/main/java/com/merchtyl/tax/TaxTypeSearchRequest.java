package com.merchtyl.tax;

public record TaxTypeSearchRequest(
        String code,
        String name,
        Boolean active,
        int page,
        int size
) {
}
