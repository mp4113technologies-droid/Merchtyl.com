package com.merchtyl.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StockCountUpdateLinesRequest(
        @NotEmpty @Valid List<StockCountLineCountRequest> lines
) {
}
