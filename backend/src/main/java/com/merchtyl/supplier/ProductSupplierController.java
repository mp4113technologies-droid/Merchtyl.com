package com.merchtyl.supplier;

import com.merchtyl.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/product-suppliers")
public class ProductSupplierController {
    private final ProductSupplierService productSupplierService;

    public ProductSupplierController(ProductSupplierService productSupplierService) {
        this.productSupplierService = productSupplierService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_MANAGE)")
    ProductSupplierResponse create(@Valid @RequestBody ProductSupplierRequest request, Authentication authentication) {
        return productSupplierService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_VIEW)")
    PageResponse<ProductSupplierResponse> list(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String supplierSku,
            @RequestParam(required = false) Boolean preferred,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productSupplierService.search(new ProductSupplierSearchRequest(
                productId,
                supplierId,
                supplierSku,
                preferred,
                active,
                page,
                size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_VIEW)")
    ProductSupplierResponse get(@PathVariable UUID id) {
        return productSupplierService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_MANAGE)")
    ProductSupplierResponse update(@PathVariable UUID id, @Valid @RequestBody ProductSupplierUpdateRequest request, Authentication authentication) {
        return productSupplierService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_MANAGE)")
    ProductSupplierResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody ProductSupplierStatusRequest request, Authentication authentication) {
        return productSupplierService.updateStatus(id, request, authentication);
    }
}
