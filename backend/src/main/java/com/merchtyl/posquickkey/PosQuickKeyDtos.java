package com.merchtyl.posquickkey;

import com.merchtyl.product.SellableType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public final class PosQuickKeyDtos {
    private PosQuickKeyDtos() {}

    public record CreateRequest(@NotNull UUID productVariantId, @Size(max = 80) String displayLabel) {}
    public record UpdateRequest(@Size(max = 80) String displayLabel, @Min(0) int displayOrder, boolean active) {}
    public record Response(UUID id, UUID productId, UUID productVariantId, String productName, String variantName,
                           String displayLabel, String sku, BigDecimal price, SellableType sellableType,
                           boolean ageRestricted, Integer minimumAge, boolean active, boolean productAvailable,
                           int displayOrder, long version) {}
}
