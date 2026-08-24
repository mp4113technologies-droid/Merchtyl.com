package com.merchtyl.supplier;

import java.util.UUID;

public record ProductSupplierSearchRequest(
        UUID productId,
        UUID supplierId,
        String supplierSku,
        Boolean preferred,
        Boolean active,
        int page,
        int size
) {
}
