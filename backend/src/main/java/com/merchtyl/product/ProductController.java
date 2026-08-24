package com.merchtyl.product;

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
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.canCreateProduct(authentication)")
    ProductResponse create(@Valid @RequestBody ProductRequest request, Authentication authentication) {
        return productService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_VIEW)")
    PageResponse<ProductResponse> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) SellableType sellableType,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) UUID unitOfMeasureId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Authentication authentication) {
        return productService.search(new ProductSearchRequest(
                name,
                sku,
                barcode,
                sellableType,
                categoryId,
                brandId,
                unitOfMeasureId,
                active,
                storeId,
                page,
                size), authentication);
    }

    @GetMapping("/barcodes/{barcode}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_VIEW)")
    PosBarcodeLookupResponse lookupBarcode(@PathVariable String barcode, @RequestParam UUID storeId, Authentication authentication) {
        return productService.lookupBarcode(barcode, storeId, authentication);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_VIEW)")
    ProductResponse get(@PathVariable UUID id, Authentication authentication) {
        return productService.get(id, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_UPDATE)")
    ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request, Authentication authentication) {
        return productService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_DEACTIVATE)")
    ProductResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody ProductStatusRequest request, Authentication authentication) {
        return productService.updateStatus(id, request, authentication);
    }
}
