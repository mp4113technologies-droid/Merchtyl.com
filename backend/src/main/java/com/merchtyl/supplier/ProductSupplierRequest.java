package com.merchtyl.supplier;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProductSupplierRequest(
        @NotNull UUID productId,
        @NotNull UUID supplierId,
        @Size(max = 128) String supplierSku,
        boolean preferred,
        boolean active
) {
}
