package com.merchtyl.foodmenu;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

public final class FoodMenuDtos {
    private FoodMenuDtos() {}
    public record CategoryRequest(@NotBlank @Size(max=120) String name, @PositiveOrZero int displayOrder, boolean active, @Size(max=1000) String imageUrl) {}
    public record CategoryResponse(UUID id, UUID storeId, String name, int displayOrder, boolean active, String imageUrl, long version) { static CategoryResponse from(FoodMenuCategory value){return new CategoryResponse(value.getId(), value.getStore().getId(), value.getName(), value.getDisplayOrder(), value.isActive(), value.getImageUrl(), value.getVersion());} }
    public record VariantRequest(@NotBlank @Size(max=120) String name,@NotNull @DecimalMin("0.00") BigDecimal price,@PositiveOrZero int displayOrder,boolean available) {}
    public record ModifierOptionRequest(@NotBlank @Size(max=120) String name,@NotNull @DecimalMin("0.00") BigDecimal priceAdjustment,@PositiveOrZero int displayOrder,boolean available) {}
    public record ModifierGroupRequest(@NotBlank @Size(max=120) String name,@PositiveOrZero int minimumSelections,@Positive int maximumSelections,@PositiveOrZero int displayOrder,@NotEmpty List<@Valid ModifierOptionRequest> options) {}
    public record ComponentRequest(@NotBlank @Size(max=120) String name,boolean includedByDefault,boolean removable,boolean allowExtra,@DecimalMin("0.00") BigDecimal extraPrice,@PositiveOrZero int displayOrder,boolean active) {}
    public record ComponentSelectionRequest(@NotNull UUID componentId,@NotNull FoodComponentSelectionState state) {}
    public record ItemRequest(UUID productId, @NotNull UUID categoryId, @NotBlank @Size(max=180) String displayName, @Size(max=1000) String description, @NotNull @DecimalMin("0.00") BigDecimal price, UUID taxCategoryId, @PositiveOrZero int displayOrder, boolean available, @Size(max=1000) String imageUrl, List<@Valid VariantRequest> variants, List<@Valid ModifierGroupRequest> modifierGroups,List<@Valid ComponentRequest> components) { public ItemRequest(UUID productId,UUID categoryId,String displayName,String description,BigDecimal price,UUID taxCategoryId,int displayOrder,boolean available,String imageUrl){this(productId,categoryId,displayName,description,price,taxCategoryId,displayOrder,available,imageUrl,List.of(),List.of(),List.of());} }
    public record VariantResponse(UUID id,String name,BigDecimal price,int displayOrder,boolean available) { static VariantResponse from(FoodMenuItemVariant v){return new VariantResponse(v.getId(),v.getName(),v.getPrice(),v.getDisplayOrder(),v.isAvailable());} }
    public record ModifierOptionResponse(UUID id,String name,BigDecimal priceAdjustment,int displayOrder,boolean available) { static ModifierOptionResponse from(FoodMenuModifierOption v){return new ModifierOptionResponse(v.getId(),v.getName(),v.getPriceAdjustment(),v.getDisplayOrder(),v.isAvailable());} }
    public record ModifierGroupResponse(UUID id,String name,int minimumSelections,int maximumSelections,int displayOrder,List<ModifierOptionResponse> options) { static ModifierGroupResponse from(FoodMenuModifierGroup v){return new ModifierGroupResponse(v.getId(),v.getName(),v.getMinimumSelections(),v.getMaximumSelections(),v.getDisplayOrder(),v.getOptions().stream().map(ModifierOptionResponse::from).toList());} }
    public record ComponentResponse(UUID id,String name,boolean includedByDefault,boolean removable,boolean allowExtra,BigDecimal extraPrice,int displayOrder,boolean active) { static ComponentResponse from(FoodMenuItemComponent v){return new ComponentResponse(v.getId(),v.getName(),v.isIncludedByDefault(),v.isRemovable(),v.isAllowExtra(),v.getExtraPrice(),v.getDisplayOrder(),v.isActive());} }
    public record ItemResponse(UUID id, UUID storeId, UUID categoryId, String categoryName, UUID productId, String productName, String displayName, String description, BigDecimal price, boolean inventoryTracked, boolean madeToOrder, int displayOrder, boolean available, String imageUrl, List<VariantResponse> variants,List<ModifierGroupResponse> modifierGroups,List<ComponentResponse> components,long version) { static ItemResponse from(FoodMenuItem value){return new ItemResponse(value.getId(), value.getStore().getId(), value.getCategory().getId(), value.getCategory().getName(), value.isLinkedProduct()?value.getProduct().getId():null, value.isLinkedProduct()?value.getProduct().getName():null, value.getDisplayName(), value.getDescription(), value.getPrice(), value.isLinkedProduct()&&value.getProduct().isInventoryTrackingEnabled(), !value.isLinkedProduct(), value.getDisplayOrder(), value.isAvailable(), value.getImageUrl(),value.getVariants().stream().map(VariantResponse::from).toList(),value.getModifierGroups().stream().map(ModifierGroupResponse::from).toList(),value.getComponents().stream().map(ComponentResponse::from).toList(), value.getVersion());} }
    public record AvailabilityRequest(boolean available) {}
    public record AddToSaleRequest(@NotNull @DecimalMin("0.001") BigDecimal quantity) {}
}
