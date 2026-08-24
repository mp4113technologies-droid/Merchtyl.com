package com.merchtyl.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record StockCountCreateRequest(
        @NotNull UUID storeId,
        @NotBlank @Size(max = 255) String reference,
        @Size(max = 2000) String notes,
        @NotEmpty @Valid List<StockCountLineCreateRequest> lines
) {
}
