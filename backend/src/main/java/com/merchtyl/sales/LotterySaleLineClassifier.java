package com.merchtyl.sales;

import com.merchtyl.product.SellableType;

import java.math.BigDecimal;

/** Shared transaction-time Lottery classification used by EOD and detailed reports. */
public final class LotterySaleLineClassifier {
    private LotterySaleLineClassifier() {}

    public static boolean isPhysicalTicket(SaleItem item) {
        return item.getSellableTypeSnapshot() == SellableType.LOTTERY_PRODUCT;
    }

    public static boolean isManualSold(SaleItem item) {
        return item.getLineType() == SaleLineType.LOTTERY_SOLD;
    }

    public static boolean isWin(SaleItem item) {
        return item.getLineType() == SaleLineType.LOTTERY_WIN;
    }

    public static boolean isLottery(SaleItem item) {
        return isPhysicalTicket(item) || isManualSold(item) || isWin(item);
    }

    public static BigDecimal reportingAmount(SaleItem item) {
        return item.getLineSubtotal().subtract(item.getDiscountAmount()).abs();
    }
}
