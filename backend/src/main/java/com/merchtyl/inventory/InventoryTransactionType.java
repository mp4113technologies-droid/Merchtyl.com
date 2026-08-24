package com.merchtyl.inventory;

public enum InventoryTransactionType {
    OPENING_STOCK,
    PURCHASE,
    SALE,
    RETURN,
    ADJUSTMENT_INCREASE,
    ADJUSTMENT_DECREASE,
    STOCK_COUNT_INCREASE,
    STOCK_COUNT_DECREASE,
    DAMAGED,
    EXPIRED,
    TRANSFER_IN,
    TRANSFER_OUT,
    VOID_REVERSAL
}
