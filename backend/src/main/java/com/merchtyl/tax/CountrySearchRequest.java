package com.merchtyl.tax;

public record CountrySearchRequest(
        String code,
        String name,
        Boolean active,
        int page,
        int size
) {
}
