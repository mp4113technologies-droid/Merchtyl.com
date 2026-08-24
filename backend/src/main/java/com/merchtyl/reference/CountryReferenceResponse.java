package com.merchtyl.reference;

import com.merchtyl.tax.Country;

import java.util.UUID;

public record CountryReferenceResponse(
        UUID id,
        String alpha2Code,
        String alpha3Code,
        String name,
        String defaultCurrencyCode,
        String defaultLanguageCode,
        boolean active,
        int displayOrder
) {
    static CountryReferenceResponse from(Country country) {
        return new CountryReferenceResponse(
                country.getId(),
                country.getCode(),
                country.getAlpha3Code(),
                country.getName(),
                country.getDefaultCurrency() == null ? null : country.getDefaultCurrency().getCode(),
                country.getDefaultLanguageCode(),
                country.isActive(),
                country.getDisplayOrder());
    }
}
