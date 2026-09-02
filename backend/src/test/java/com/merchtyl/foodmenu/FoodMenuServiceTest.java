package com.merchtyl.foodmenu;

import com.merchtyl.common.ConflictException;
import com.merchtyl.product.*;
import com.merchtyl.sales.SaleService;
import com.merchtyl.store.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static com.merchtyl.foodmenu.FoodMenuDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoodMenuServiceTest {
    private final StoreCapabilityService capabilityService=mock(StoreCapabilityService.class);
    private final FoodMenuCategoryRepository categories=mock(FoodMenuCategoryRepository.class);
    private final FoodMenuItemRepository items=mock(FoodMenuItemRepository.class);
    private final ProductRepository products=mock(ProductRepository.class);
    private final StoreProductRepository storeProducts=mock(StoreProductRepository.class);
    private final SaleService sales=mock(SaleService.class);
    private final Authentication authentication=mock(Authentication.class);
    private final FoodMenuService service=new FoodMenuService(capabilityService,categories,items,products,storeProducts,sales);

    @Test void createsCategoryForCapabilityCheckedStore(){var store=mock(Store.class);when(store.getTenantId()).thenReturn(UUID.randomUUID());when(capabilityService.requireCapability(any(),eq(StoreCapability.FOOD_SERVICE),eq(authentication))).thenReturn(store);when(categories.save(any())).thenAnswer(invocation->invocation.getArgument(0));var result=service.createCategory(UUID.randomUUID(),new CategoryRequest("Pizza",2,true,null),authentication);assertThat(result.name()).isEqualTo("Pizza");verify(capabilityService).requireCapability(any(),eq(StoreCapability.FOOD_SERVICE),eq(authentication));}
    @Test void rejectsProductWithoutFoodCapability(){var store=mock(Store.class);var category=mock(FoodMenuCategory.class);var product=mock(Product.class);var storeId=UUID.randomUUID();var productId=UUID.randomUUID();when(store.getTenantId()).thenReturn(UUID.randomUUID());when(capabilityService.requireCapability(storeId,StoreCapability.FOOD_SERVICE,authentication)).thenReturn(store);when(categories.findByIdAndStoreId(any(),eq(storeId))).thenReturn(Optional.of(category));when(products.findByIdAndTenantId(eq(productId),any())).thenReturn(Optional.of(product));when(product.hasCapability(ProductCapability.FOOD_SERVICE)).thenReturn(false);assertThatThrownBy(()->service.createItem(storeId,new ItemRequest(productId,UUID.randomUUID(),"Pizza",null,BigDecimal.TEN,null,0,true,null),authentication)).isInstanceOf(ConflictException.class);}
    @Test void createsMadeToOrderItemWithoutRetailProductOrInventory(){var store=mock(Store.class);var storeId=UUID.randomUUID();var category=mock(FoodMenuCategory.class);when(store.getId()).thenReturn(storeId);when(store.getTenantId()).thenReturn(UUID.randomUUID());when(capabilityService.requireCapability(storeId,StoreCapability.FOOD_SERVICE,authentication)).thenReturn(store);when(categories.findByIdAndStoreId(any(),eq(storeId))).thenReturn(Optional.of(category));when(products.saveAndFlush(any())).thenAnswer(invocation->invocation.getArgument(0));when(items.saveAndFlush(any())).thenAnswer(invocation->invocation.getArgument(0));var result=service.createItem(storeId,new ItemRequest(null,UUID.randomUUID(),"Samosa","Vegetable samosa",new BigDecimal("2.49"),null,1,true,null),authentication);assertThat(result.madeToOrder()).isTrue();assertThat(result.inventoryTracked()).isFalse();assertThat(result.productId()).isNull();verify(storeProducts).saveAndFlush(any(StoreProduct.class));}
    @Test void soldOutItemCannotBeAddedToSale(){var storeId=UUID.randomUUID();var itemId=UUID.randomUUID();var value=mock(FoodMenuItem.class);when(capabilityService.requireCapability(storeId,StoreCapability.FOOD_SERVICE,authentication)).thenReturn(mock(Store.class));when(items.findByIdAndStoreId(itemId,storeId)).thenReturn(Optional.of(value));when(value.isAvailable()).thenReturn(false);assertThatThrownBy(()->service.addToSale(storeId,itemId,UUID.randomUUID(),new AddToSaleRequest(BigDecimal.ONE),authentication)).isInstanceOf(ConflictException.class);verifyNoInteractions(sales);}
}
