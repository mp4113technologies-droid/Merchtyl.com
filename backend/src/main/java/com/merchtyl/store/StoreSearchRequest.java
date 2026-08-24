package com.merchtyl.store;

public record StoreSearchRequest(
        String code,
        String name,
        String countryCode,
        String administrativeAreaCode,
        String currencyCode,
        Boolean active,
        Boolean pricesIncludeTax,
        Boolean negativeStockAllowed,
        int page,
        int size
) {
}
