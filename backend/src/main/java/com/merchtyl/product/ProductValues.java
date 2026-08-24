package com.merchtyl.product;

import com.merchtyl.catalogue.Brand;
import com.merchtyl.catalogue.Category;
import com.merchtyl.catalogue.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProductValues(
        String sku,
        String name,
        String description,
        SellableType sellableType,
        UnitOfMeasure unitOfMeasure,
        BigDecimal cost,
        BigDecimal price,
        Category category,
        Brand brand,
        boolean active,
        boolean inventoryTrackingEnabled,
        boolean decimalQuantityAllowed,
        String imageUrl,
        UUID taxCategoryId,
        List<ProductVariantValues> variants,
        List<ProductBarcodeValues> barcodes,
        Set<ProductCapability> capabilities
) {
}
