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
@RequestMapping("/api/v1/suppliers")
public class SupplierController {
    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_MANAGE)")
    SupplierResponse create(@Valid @RequestBody SupplierRequest request, Authentication authentication) {
        return supplierService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_VIEW)")
    PageResponse<SupplierResponse> list(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return supplierService.search(new SupplierSearchRequest(code, name, contactName, email, active, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_VIEW)")
    SupplierResponse get(@PathVariable UUID id) {
        return supplierService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_MANAGE)")
    SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody SupplierUpdateRequest request, Authentication authentication) {
        return supplierService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_MANAGE)")
    SupplierResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody SupplierStatusRequest request, Authentication authentication) {
        return supplierService.updateStatus(id, request, authentication);
    }
}
