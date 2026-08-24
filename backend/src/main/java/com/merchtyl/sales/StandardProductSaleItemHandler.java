package com.merchtyl.sales;

import com.merchtyl.product.SellableType;
import org.springframework.stereotype.Component;

@Component
public class StandardProductSaleItemHandler extends AbstractSaleItemHandler {
    @Override
    public SellableType supportedType() {
        return SellableType.STANDARD_PRODUCT;
    }

    @Override
    public void validate(SaleItemRequest request) {
        validateCommon(request);
    }
}
