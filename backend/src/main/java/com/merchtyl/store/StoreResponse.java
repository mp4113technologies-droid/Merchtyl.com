package com.merchtyl.store;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;

public record StoreResponse(
        UUID id,
        String code,
        String name,
        String legalName,
        String countryCode,
        UUID countryId,
        String administrativeAreaCode,
        String administrativeDivisionCode,
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
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version,
        Set<StoreCapability> capabilities,
        boolean foodServiceEnabled,
        String kitchenDisplayName,
        long kitchenUsersCount
) {
    public StoreResponse(UUID id, String code, String name, String legalName, String countryCode, UUID countryId,
                         String administrativeAreaCode, String administrativeDivisionCode, UUID administrativeDivisionId,
                         String address, String phone, String email, String currencyCode, UUID currencyId, String locale,
                         String timezone, UUID timezoneId, String timezoneName, UUID taxRegionId, String taxRegionCode,
                         boolean pricesIncludeTax, boolean negativeStockAllowed, boolean active, Instant createdAt,
                         Instant updatedAt, long version) {
        this(id, code, name, legalName, countryCode, countryId, administrativeAreaCode, administrativeDivisionCode,
                administrativeDivisionId, address, phone, email, currencyCode, currencyId, locale, timezone, timezoneId,
                timezoneName, taxRegionId, taxRegionCode, pricesIncludeTax, negativeStockAllowed, active, createdAt,
                updatedAt, version, Set.of(StoreCapability.RETAIL), false, null, 0);
    }
    static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getCode(),
                store.getName(),
                store.getLegalName(),
                store.getCountryCode(),
                store.getCountryId(),
                store.getAdministrativeAreaCode(),
                store.getAdministrativeAreaCode(),
                store.getAdministrativeDivisionId(),
                store.getAddress(),
                store.getPhone(),
                store.getEmail(),
                store.getCurrencyCode(),
                store.getCurrencyId(),
                store.getLocale(),
                store.getTimezone(),
                store.getTimezoneId(),
                store.getTimezoneName(),
                store.getTaxRegionId(),
                store.getTaxRegionCode(),
                store.isPricesIncludeTax(),
                store.isNegativeStockAllowed(),
                store.isActive(),
                store.getCreatedAt(),
                store.getUpdatedAt(),
                store.getVersion(), store.getCapabilities(), store.isFoodServiceEnabled(), store.getKitchenDisplayName(), 0);
    }

    StoreResponse withKitchenUsersCount(long count) {
        return new StoreResponse(id, code, name, legalName, countryCode, countryId, administrativeAreaCode,
                administrativeDivisionCode, administrativeDivisionId, address, phone, email, currencyCode, currencyId,
                locale, timezone, timezoneId, timezoneName, taxRegionId, taxRegionCode, pricesIncludeTax,
                negativeStockAllowed, active, createdAt, updatedAt, version, capabilities, foodServiceEnabled,
                kitchenDisplayName, count);
    }
}
