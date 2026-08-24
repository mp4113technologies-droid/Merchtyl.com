package com.merchtyl.product;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/products/{productId}/stores")
public class StoreProductController {
 private final StoreProductService service;
 public StoreProductController(StoreProductService service){this.service=service;}
 @GetMapping @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_VIEW)")
 List<StoreProductResponse> list(@PathVariable UUID productId, Authentication authentication){return service.list(productId, authentication);}
 @PutMapping @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_UPDATE)")
 List<StoreProductResponse> replace(@PathVariable UUID productId, @Valid @RequestBody List<@Valid StoreProductRequest> requests, Authentication authentication){return service.replace(productId, requests, authentication);}
}
