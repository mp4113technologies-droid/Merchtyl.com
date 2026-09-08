package com.merchtyl.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SaleCheckoutRequest(
        @NotNull UUID registerSessionId,
        String saleChannel,
        @NotEmpty List<@Valid SaleCheckoutItemRequest> items,
        @Valid SaleCheckoutDiscountRequest discount
) {
    public SaleCheckoutRequest(UUID registerSessionId, String saleChannel, List<SaleCheckoutItemRequest> items) {
        this(registerSessionId, saleChannel, items, null);
    }
}
