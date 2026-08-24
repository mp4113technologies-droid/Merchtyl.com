package com.merchtyl.inventory;

public enum StockAdjustmentType {
    INCREASE,
    DECREASE,
    DAMAGED,
    EXPIRED;

    InventoryTransactionType transactionType() {
        return switch (this) {
            case INCREASE -> InventoryTransactionType.ADJUSTMENT_INCREASE;
            case DECREASE -> InventoryTransactionType.ADJUSTMENT_DECREASE;
            case DAMAGED -> InventoryTransactionType.DAMAGED;
            case EXPIRED -> InventoryTransactionType.EXPIRED;
        };
    }

    int directionMultiplier() {
        return this == INCREASE ? 1 : -1;
    }
}
