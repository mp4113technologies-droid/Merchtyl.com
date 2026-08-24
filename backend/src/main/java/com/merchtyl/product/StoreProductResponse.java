package com.merchtyl.product;
import java.math.BigDecimal; import java.util.UUID;
public record StoreProductResponse(UUID id, UUID tenantId, UUID storeId, UUID productId, boolean active, boolean sellable,
 BigDecimal sellingPrice, BigDecimal costPrice, BigDecimal minimumSellingPrice, BigDecimal lowStockThreshold,
 boolean allowDiscount, boolean allowPriceOverride, long version) {
 static StoreProductResponse from(StoreProduct value){return new StoreProductResponse(value.getId(),value.getTenantId(),value.getStore().getId(),value.getProduct().getId(),value.isActive(),value.isSellable(),value.getSellingPrice(),value.getCostPrice(),value.getMinimumSellingPrice(),value.getLowStockThreshold(),value.isAllowDiscount(),value.isAllowPriceOverride(),value.getVersion());}
}
