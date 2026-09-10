package com.merchtyl.product;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/product-variants/{variantId}/barcodes")
public class ProductVariantBarcodeController {
    private final ProductService products;
    public ProductVariantBarcodeController(ProductService products) { this.products = products; }

    @PostMapping("/bulk")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_BARCODE_MANAGE)")
    BulkVariantBarcodeResponse addAll(@PathVariable UUID variantId, @Valid @RequestBody BulkVariantBarcodeRequest request,
                                      Authentication authentication) {
        return products.addVariantBarcodes(variantId, request, authentication);
    }
}
