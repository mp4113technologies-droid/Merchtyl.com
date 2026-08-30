package com.merchtyl.foodmenu;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class FoodMenuDtos {
    private FoodMenuDtos() {}
    public record CategoryRequest(@NotBlank @Size(max=120) String name, @PositiveOrZero int displayOrder, boolean active, @Size(max=1000) String imageUrl) {}
    public record CategoryResponse(UUID id, UUID storeId, String name, int displayOrder, boolean active, String imageUrl, long version) { static CategoryResponse from(FoodMenuCategory value){return new CategoryResponse(value.getId(), value.getStore().getId(), value.getName(), value.getDisplayOrder(), value.isActive(), value.getImageUrl(), value.getVersion());} }
    public record ItemRequest(@NotNull UUID productId, @NotNull UUID categoryId, @NotBlank @Size(max=180) String displayName, @NotNull @DecimalMin("0.00") BigDecimal price, @PositiveOrZero int displayOrder, boolean available, @Size(max=1000) String imageUrl) {}
    public record ItemResponse(UUID id, UUID storeId, UUID categoryId, String categoryName, UUID productId, String productName, String displayName, BigDecimal price, int displayOrder, boolean available, String imageUrl, long version) { static ItemResponse from(FoodMenuItem value){return new ItemResponse(value.getId(), value.getStore().getId(), value.getCategory().getId(), value.getCategory().getName(), value.getProduct().getId(), value.getProduct().getName(), value.getDisplayName(), value.getPrice(), value.getDisplayOrder(), value.isAvailable(), value.getImageUrl(), value.getVersion());} }
    public record AvailabilityRequest(boolean available) {}
    public record AddToSaleRequest(@NotNull @DecimalMin("0.001") BigDecimal quantity) {}
}
