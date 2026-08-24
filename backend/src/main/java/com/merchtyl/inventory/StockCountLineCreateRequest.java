package com.merchtyl.inventory;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record StockCountLineCreateRequest(
        @NotNull UUID productId,
        BigDecimal countedQuantity
) {
}
