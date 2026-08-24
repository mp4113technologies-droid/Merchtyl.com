package com.merchtyl.sales;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.SellableType;
import org.springframework.stereotype.Component;

@Component
public class ServiceSaleItemHandler extends AbstractSaleItemHandler {
    @Override
    public SellableType supportedType() {
        return SellableType.SERVICE;
    }

    @Override
    public void validate(SaleItemRequest request) {
        validateCommon(request);
        if (request.product().hasCapability(ProductCapability.TRACK_INVENTORY)) {
            throw new BadRequestException("Services cannot track inventory");
        }
    }
}
