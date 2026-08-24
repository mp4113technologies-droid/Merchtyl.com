package com.merchtyl.reference;

import java.util.UUID;

public record CurrencyResponse(
        UUID id,
        String code,
        String name,
        String symbol,
        int decimalPlaces,
        boolean active
) {
    static CurrencyResponse from(Currency currency) {
        return new CurrencyResponse(
                currency.getId(),
                currency.getCode(),
                currency.getName(),
                currency.getSymbol(),
                currency.getDecimalPlaces(),
                currency.isActive());
    }
}
