package com.merchtyl.store;

import java.util.UUID;

record StoreValues(
        String code,
        String name,
        String legalName,
        String countryCode,
        UUID countryId,
        String administrativeAreaCode,
        UUID administrativeDivisionId,
        String address,
        String phone,
        String email,
        String currencyCode,
        UUID currencyId,
        String locale,
        String timezone,
        UUID timezoneId,
        String timezoneName,
        UUID taxRegionId,
        String taxRegionCode,
        boolean pricesIncludeTax,
        boolean negativeStockAllowed,
        boolean active
) {
}
