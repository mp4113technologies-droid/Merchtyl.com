package com.merchtyl.sales;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.product.SellableType;
import org.springframework.stereotype.Component;

/** Validates configured, barcode-backed lottery products sold through Retail POS. */
@Component
public class LotteryProductSaleItemHandler extends AbstractSaleItemHandler {
    @Override
    public SellableType supportedType() {
        return SellableType.LOTTERY_PRODUCT;
    }

    @Override
    public void validate(SaleItemRequest request) {
        validateCommon(request);
        if (request.priceOverride()) {
            throw new BadRequestException("LOTTERY_PRICE_OVERRIDE_NOT_ALLOWED");
        }
        if (request.discountAmount().signum() != 0) {
            throw new BadRequestException("LOTTERY_DISCOUNT_NOT_ALLOWED");
        }
    }
}
