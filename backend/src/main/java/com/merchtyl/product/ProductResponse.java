package com.merchtyl.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        SellableType sellableType,
        UUID unitOfMeasureId,
        BigDecimal cost,
        BigDecimal price,
        UUID categoryId,
        UUID brandId,
        boolean active,
        boolean inventoryTrackingEnabled,
        boolean decimalQuantityAllowed,
        String imageUrl,
        UUID taxCategoryId,
        List<ProductVariantResponse> variants,
        List<ProductBarcodeResponse> barcodes,
        Set<ProductCapability> capabilities,
        Instant createdAt,
        Instant updatedAt,
        long version,
        Integer minimumAge
) {
    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getSellableType(),
                product.getUnitOfMeasure() == null ? null : product.getUnitOfMeasure().getId(),
                product.getCost(),
                product.getPrice(),
                product.getCategory() == null ? null : product.getCategory().getId(),
                product.getBrand() == null ? null : product.getBrand().getId(),
                product.isActive(),
                product.isInventoryTrackingEnabled(),
                product.isDecimalQuantityAllowed(),
                product.getImageUrl(),
                product.getTaxCategoryId(),
                product.getVariants().stream().map(ProductVariantResponse::from).toList(),
                product.getBarcodes().stream().map(ProductBarcodeResponse::from).toList(),
                product.getCapabilities(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion(), product.getMinimumAge());
    }

    ProductResponse withPrice(BigDecimal effectivePrice) {
        return new ProductResponse(id, sku, name, description, sellableType, unitOfMeasureId, cost, effectivePrice,
                categoryId, brandId, active, inventoryTrackingEnabled, decimalQuantityAllowed, imageUrl, taxCategoryId,
                variants, barcodes, capabilities, createdAt, updatedAt, version, minimumAge);
    }

    public ProductResponse(UUID id, String sku, String name, String description, SellableType sellableType,
                           UUID unitOfMeasureId, BigDecimal cost, BigDecimal price, UUID categoryId, UUID brandId,
                           boolean active, boolean inventoryTrackingEnabled, boolean decimalQuantityAllowed,
                           String imageUrl, UUID taxCategoryId, List<ProductVariantResponse> variants,
                           List<ProductBarcodeResponse> barcodes, Set<ProductCapability> capabilities,
                           Instant createdAt, Instant updatedAt, long version) {
        this(id, sku, name, description, sellableType, unitOfMeasureId, cost, price, categoryId, brandId, active,
                inventoryTrackingEnabled, decimalQuantityAllowed, imageUrl, taxCategoryId, variants, barcodes,
                capabilities, createdAt, updatedAt, version, null);
    }
}
