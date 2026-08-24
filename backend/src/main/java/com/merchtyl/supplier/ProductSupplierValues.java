package com.merchtyl.supplier;

import com.merchtyl.product.Product;

public record ProductSupplierValues(
        Product product,
        Supplier supplier,
        String supplierSku,
        boolean preferred,
        boolean active
) {
}
