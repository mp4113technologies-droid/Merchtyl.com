package com.merchtyl.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record StockAdjustmentRequest(
        @NotNull UUID storeId,
        @NotBlank @Size(max = 255) String reason,
        @Size(max = 2000) String notes,
        @Size(max = 1000) String approvalNotes,
        @NotEmpty @Valid List<StockAdjustmentLineRequest> lines
) {
}
