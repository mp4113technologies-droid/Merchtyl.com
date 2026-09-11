package com.merchtyl.discount;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1")
public class DiscountDefinitionController {
    private static final String VIEW="@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).DISCOUNT_VIEW)";
    private static final String MANAGE="@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).DISCOUNT_MANAGE)";
    private final DiscountDefinitionService service;
    public DiscountDefinitionController(DiscountDefinitionService service){this.service=service;}
    @GetMapping("/discounts") @PreAuthorize(VIEW) List<DiscountDefinitionResponse> list(Authentication auth){return service.list(auth);}
    @PostMapping("/discounts") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MANAGE) DiscountDefinitionResponse create(@Valid @RequestBody DiscountDefinitionRequest request,Authentication auth){return service.create(request,auth);}
    @PutMapping("/discounts/{id}") @PreAuthorize(MANAGE) DiscountDefinitionResponse update(@PathVariable UUID id,@Valid @RequestBody DiscountDefinitionRequest request,Authentication auth){return service.update(id,request,auth);}
    @DeleteMapping("/discounts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize(MANAGE) void delete(@PathVariable UUID id,Authentication auth){service.delete(id,auth);}
    @GetMapping("/stores/{storeId}/discounts") @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).POS_SALE_DISCOUNT) or @authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).POS_ACCESS) or @authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FOOD_POS_ACCESS)") List<DiscountDefinitionResponse> active(@PathVariable UUID storeId,Authentication auth){return service.activeForStore(storeId,auth);}
}
