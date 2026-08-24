package com.merchtyl.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ReturnCreateRequest(
        @NotNull UUID originalSaleId,
        @Size(max = 1000) String reason,
        @NotEmpty List<@Valid ReturnItemRequest> items
) {
}
