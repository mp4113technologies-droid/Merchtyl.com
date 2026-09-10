package com.merchtyl.initialinventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class InitialInventoryDtos {
    private InitialInventoryDtos() {}
    public record RawRow(int rowNumber,String productReference,String productName,String variantName,String barcode,String sku,
                         BigDecimal sellingPrice,BigDecimal costPrice,BigDecimal openingQuantity,BigDecimal lowStockLevel,
                         String category,String brand,String unitCode,String taxCategory,String supplier,Boolean ageRestricted,Boolean active) {}
    public record RowPreview(int rowNumber,String productReference,String productName,String variantName,String barcode,String sku,
                             BigDecimal sellingPrice,BigDecimal openingQuantity,List<String> actions,List<String> errors) {
        static RowPreview from(InitialInventoryImportRow row){return new RowPreview(row.getRowNumber(),row.getProductReference(),row.getProductName(),row.getVariantName(),row.getBarcode(),row.getSku(),row.getSellingPrice(),row.getOpeningQuantity(),List.of(row.getProductAction(),row.getVariantAction(),"ASSIGN_PRODUCT_TO_STORE","CREATE_OPENING_INVENTORY"),row.errors());}
    }
    public record ValidationResponse(UUID importId,UUID storeId,int totalVariantRows,int productGroups,int validRows,int warningRows,int errorRows,boolean canImport,List<RowPreview> rows) {}
    public record ImportResult(UUID importId,UUID storeId,int productsCreated,int productsReused,int variantsCreated,int variantsReused,int storeAssignments,int openingInventory) {}
}
