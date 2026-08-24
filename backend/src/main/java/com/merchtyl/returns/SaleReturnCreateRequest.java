package com.merchtyl.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaleReturnCreateRequest(
        @Size(max = 1000) String reason,
        @NotEmpty List<@Valid ReturnItemRequest> items
) {
}
