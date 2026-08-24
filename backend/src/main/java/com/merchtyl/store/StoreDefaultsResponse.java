package com.merchtyl.store;

public record StoreDefaultsResponse(
        String countryCode,
        String administrativeDivisionCode,
        String currencyCode,
        String locale,
        String timezone,
        String taxRegionCode
) {
}
