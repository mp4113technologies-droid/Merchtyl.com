package com.merchtyl.sales;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SaleCreateDraftRequest(
        @NotNull UUID registerSessionId,
        UUID customerId,
        String saleChannel
) {
}
