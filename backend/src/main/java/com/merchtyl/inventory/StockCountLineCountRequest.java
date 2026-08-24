package com.merchtyl.inventory;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record StockCountLineCountRequest(
        @NotNull UUID lineId,
        @NotNull BigDecimal countedQuantity
) {
}
