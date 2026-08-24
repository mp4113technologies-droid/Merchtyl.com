package com.merchtyl.sales;

import com.merchtyl.product.SellableType;

public interface SaleItemHandler {
    SellableType supportedType();

    void validate(SaleItemRequest request);
}
