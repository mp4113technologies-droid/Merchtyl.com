package com.merchtyl.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

import java.util.List;
import java.util.UUID;

public record SaleCheckoutRequest(
        @NotNull UUID registerSessionId,
        String saleChannel,
        @NotNull List<@Valid SaleCheckoutItemRequest> items,
        @Valid SaleCheckoutDiscountRequest discount,
        @DecimalMin(value = "0.01") BigDecimal cashbackAmount
) {
    public SaleCheckoutRequest(UUID registerSessionId, String saleChannel, List<SaleCheckoutItemRequest> items) {
        this(registerSessionId, saleChannel, items, null, null);
    }

    public SaleCheckoutRequest(UUID registerSessionId, String saleChannel, List<SaleCheckoutItemRequest> items,
            SaleCheckoutDiscountRequest discount) { this(registerSessionId, saleChannel, items, discount, null); }
}
