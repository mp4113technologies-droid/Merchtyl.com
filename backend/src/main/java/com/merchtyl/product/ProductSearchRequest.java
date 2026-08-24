package com.merchtyl.product;

import java.util.UUID;

public record ProductSearchRequest(
        String name,
        String sku,
        String barcode,
        SellableType sellableType,
        UUID categoryId,
        UUID brandId,
        UUID unitOfMeasureId,
        Boolean active,
        UUID storeId,
        int page,
        int size
) {
}
