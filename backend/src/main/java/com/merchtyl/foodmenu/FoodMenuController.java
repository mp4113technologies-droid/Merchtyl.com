package com.merchtyl.foodmenu;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.merchtyl.foodmenu.FoodMenuDtos.*;
import com.merchtyl.sales.SaleResponse;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/food-menu")
public class FoodMenuController {
    private static final String VIEW="@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).FOOD_POS_ACCESS)";
    private static final String MANAGE="@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_MANAGE) or @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).FOOD_ORDER_UPDATE)";
    private final FoodMenuService service;
    public FoodMenuController(FoodMenuService service){this.service=service;}
    @GetMapping("/categories") @PreAuthorize(VIEW) List<CategoryResponse> categories(@PathVariable UUID storeId,Authentication auth){return service.categories(storeId,auth);}
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MANAGE) CategoryResponse createCategory(@PathVariable UUID storeId,@Valid @RequestBody CategoryRequest request,Authentication auth){return service.createCategory(storeId,request,auth);}
    @PutMapping("/categories/{id}") @PreAuthorize(MANAGE) CategoryResponse updateCategory(@PathVariable UUID storeId,@PathVariable UUID id,@Valid @RequestBody CategoryRequest request,Authentication auth){return service.updateCategory(storeId,id,request,auth);}
    @DeleteMapping("/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize(MANAGE) void deleteCategory(@PathVariable UUID storeId,@PathVariable UUID id,Authentication auth){service.deleteCategory(storeId,id,auth);}
    @GetMapping("/items") @PreAuthorize(VIEW) List<ItemResponse> items(@PathVariable UUID storeId,Authentication auth){return service.items(storeId,auth);}
    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MANAGE) ItemResponse createItem(@PathVariable UUID storeId,@Valid @RequestBody ItemRequest request,Authentication auth){return service.createItem(storeId,request,auth);}
    @PutMapping("/items/{id}") @PreAuthorize(MANAGE) ItemResponse updateItem(@PathVariable UUID storeId,@PathVariable UUID id,@Valid @RequestBody ItemRequest request,Authentication auth){return service.updateItem(storeId,id,request,auth);}
    @PatchMapping("/items/{id}/availability") @PreAuthorize(MANAGE) ItemResponse availability(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody AvailabilityRequest request,Authentication auth){return service.availability(storeId,id,request,auth);}
    @DeleteMapping("/items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize(MANAGE) void deleteItem(@PathVariable UUID storeId,@PathVariable UUID id,Authentication auth){service.deleteItem(storeId,id,auth);}
    @PostMapping("/items/{id}/sales/{saleId}") @PreAuthorize(VIEW) SaleResponse addToSale(@PathVariable UUID storeId,@PathVariable UUID id,@PathVariable UUID saleId,@Valid @RequestBody AddToSaleRequest request,Authentication auth){return service.addToSale(storeId,id,saleId,request,auth);}
}
