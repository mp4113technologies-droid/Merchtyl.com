package com.merchtyl.store;

import java.util.Set;

public record StoreDefaultsResponse(
        String countryCode,
        String administrativeDivisionCode,
        String currencyCode,
        String locale,
        String timezone,
        String taxRegionCode,
        Set<StoreCapability> capabilities,
        String kitchenDisplayName
) {
}
