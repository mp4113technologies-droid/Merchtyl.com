package com.merchtyl.foodmenu;

import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.store.StoreCapability;
import com.merchtyl.store.StoreCapabilityService;
import com.merchtyl.sales.SaleResponse;
import com.merchtyl.sales.SaleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.merchtyl.foodmenu.FoodMenuDtos.*;

@Service
public class FoodMenuService {
    private final StoreCapabilityService storeCapabilities;
    private final FoodMenuCategoryRepository categories;
    private final FoodMenuItemRepository items;
    private final ProductRepository products;
    private final SaleService sales;
    public FoodMenuService(StoreCapabilityService storeCapabilities, FoodMenuCategoryRepository categories, FoodMenuItemRepository items, ProductRepository products, SaleService sales) { this.storeCapabilities=storeCapabilities; this.categories=categories; this.items=items; this.products=products; this.sales=sales; }

    @Transactional(readOnly=true) public List<CategoryResponse> categories(UUID storeId, Authentication auth) { requireStore(storeId, auth); return categories.findAllByStoreIdOrderByDisplayOrderAscNameAsc(storeId).stream().map(CategoryResponse::from).toList(); }
    @Transactional public CategoryResponse createCategory(UUID storeId, CategoryRequest request, Authentication auth) { var store=requireStore(storeId, auth); requireUniqueCategory(storeId, request.name(), null); return CategoryResponse.from(categories.save(new FoodMenuCategory(store, request.name(), request.displayOrder(), request.active(), request.imageUrl()))); }
    @Transactional public CategoryResponse updateCategory(UUID storeId, UUID id, CategoryRequest request, Authentication auth) { requireStore(storeId, auth); var value=category(storeId,id); requireUniqueCategory(storeId,request.name(),id); value.update(request.name(),request.displayOrder(),request.active(),request.imageUrl()); return CategoryResponse.from(value); }
    @Transactional public void deleteCategory(UUID storeId, UUID id, Authentication auth) { requireStore(storeId,auth); categories.delete(category(storeId,id)); }
    @Transactional(readOnly=true) public List<ItemResponse> items(UUID storeId, Authentication auth) { requireStore(storeId,auth); return items.findAllByStoreIdOrderByCategoryDisplayOrderAscDisplayOrderAscDisplayNameAsc(storeId).stream().map(ItemResponse::from).toList(); }
    @Transactional public ItemResponse createItem(UUID storeId, ItemRequest request, Authentication auth) { var store=requireStore(storeId,auth); requireUniqueItem(storeId,request.productId(),null); var product=products.findByIdAndTenantId(request.productId(),store.getTenantId()).orElseThrow(()->new NotFoundException("Product not found")); if (!product.hasCapability(ProductCapability.FOOD_SERVICE)) throw new ConflictException("Product must support FOOD_SERVICE"); return ItemResponse.from(items.save(new FoodMenuItem(store,category(storeId,request.categoryId()),product,request.displayName(),request.price(),request.displayOrder(),request.available(),request.imageUrl()))); }
    @Transactional public ItemResponse updateItem(UUID storeId, UUID id, ItemRequest request, Authentication auth) { var store=requireStore(storeId,auth); var value=item(storeId,id); requireUniqueItem(storeId,request.productId(),id); if (!value.getProduct().getId().equals(request.productId())) throw new ConflictException("Menu item product cannot be changed"); if (!value.getProduct().getTenantId().equals(store.getTenantId())) throw new NotFoundException("Product not found"); value.update(category(storeId,request.categoryId()),request.displayName(),request.price(),request.displayOrder(),request.available(),request.imageUrl()); return ItemResponse.from(value); }
    @Transactional public ItemResponse availability(UUID storeId, UUID id, AvailabilityRequest request, Authentication auth) { requireStore(storeId,auth); var value=item(storeId,id); value.update(value.getCategory(),value.getDisplayName(),value.getPrice(),value.getDisplayOrder(),request.available(),value.getImageUrl()); return ItemResponse.from(value); }
    @Transactional public void deleteItem(UUID storeId, UUID id, Authentication auth) { requireStore(storeId,auth); items.delete(item(storeId,id)); }
    @Transactional public SaleResponse addToSale(UUID storeId, UUID id, UUID saleId, AddToSaleRequest request, Authentication auth) { requireStore(storeId,auth); var value=item(storeId,id); if(!value.isAvailable()) throw new ConflictException("Food menu item is sold out"); return sales.addFoodMenuItem(saleId,storeId,value.getProduct().getId(),request.quantity(),value.getPrice(),auth); }
    private com.merchtyl.store.Store requireStore(UUID id, Authentication auth){return storeCapabilities.requireCapability(id, StoreCapability.FOOD_SERVICE, auth);}
    private FoodMenuCategory category(UUID storeId,UUID id){return categories.findByIdAndStoreId(id,storeId).orElseThrow(()->new NotFoundException("Food category not found"));}
    private FoodMenuItem item(UUID storeId,UUID id){return items.findByIdAndStoreId(id,storeId).orElseThrow(()->new NotFoundException("Food menu item not found"));}
    private void requireUniqueCategory(UUID storeId,String name,UUID id){if(categories.existsByStoreIdAndNameIgnoreCaseAndIdNot(storeId,name,id==null?new UUID(0,0):id))throw new ConflictException("Food category name already exists");}
    private void requireUniqueItem(UUID storeId,UUID productId,UUID id){if(items.existsByStoreIdAndProductIdAndIdNot(storeId,productId,id==null?new UUID(0,0):id))throw new ConflictException("Product is already on this food menu");}
}
