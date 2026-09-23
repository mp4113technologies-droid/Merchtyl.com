package com.merchtyl.inventoryimport;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class InventoryImportDtos {
    private InventoryImportDtos() {}
    public enum Operation { SET_COUNT, ADD_STOCK, NO_CHANGE }
    public record ParsedRow(int rowNumber,UUID variantId,BigDecimal downloadedStock,BigDecimal countedStock,BigDecimal addStock) {}
    public record PreviewRow(int rowNumber,UUID productId,UUID variantId,String productCode,String productName,String variant,String sku,String barcode,BigDecimal downloadedStock,BigDecimal currentStock,Operation operation,BigDecimal enteredQuantity,BigDecimal adjustment,BigDecimal finalStock,boolean stockChanged,List<String> errors) {}
    public record ValidationResponse(UUID importId,UUID storeId,int changedRows,int unchangedRows,int errorRows,boolean canConfirm,List<PreviewRow> rows) {}
    public record ConfirmResponse(UUID importId,UUID storeId,int changedRows,List<PreviewRow> rows) {}
}
