package com.merchtyl.sales;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.SellableType;
import org.springframework.stereotype.Component;

@Component
public class WeightedProductSaleItemHandler extends AbstractSaleItemHandler {
    @Override
    public SellableType supportedType() {
        return SellableType.WEIGHTED_PRODUCT;
    }

    @Override
    public void validate(SaleItemRequest request) {
        validateCommon(request);
        if (!request.product().hasCapability(ProductCapability.ALLOW_DECIMAL_QUANTITY)) {
            throw new BadRequestException("Weighted products must allow decimal quantities");
        }
    }
}
