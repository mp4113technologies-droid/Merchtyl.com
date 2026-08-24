package com.merchtyl.store;

import com.merchtyl.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/stores")
@Tag(name = "Store Geography", description = "Tenant-authorized store setup with country, division, currency, timezone, and tax-region validation.")
public class StoreController {
    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create store", description = "Creates a store from authenticated context. Country, province/state, currency, timezone, and tax-region codes are validated against reference data; tenant IDs are not accepted in the request.")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_CREATE) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    StoreResponse create(@Valid @RequestBody StoreRequest request, Authentication authentication) {
        return storeService.create(request, authentication);
    }

    @GetMapping
    @Operation(summary = "List stores")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_VIEW) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_ACCESS)")
    PageResponse<StoreResponse> list(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String administrativeAreaCode,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean pricesIncludeTax,
            @RequestParam(required = false) Boolean negativeStockAllowed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return storeService.search(new StoreSearchRequest(
                code,
                name,
                countryCode,
                administrativeAreaCode,
                currencyCode,
                active,
                pricesIncludeTax,
                negativeStockAllowed,
                page,
                size), authentication);
    }

    @GetMapping("/defaults")
    @Operation(summary = "Get tenant store defaults", description = "Returns merchant geography defaults for pre-filling new tenant stores. Tenant identity is derived from authentication.")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_CREATE) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    StoreDefaultsResponse defaults(Authentication authentication) {
        return storeService.defaults(authentication);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get store")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_VIEW) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_ACCESS)")
    StoreResponse get(@PathVariable UUID id, Authentication authentication) {
        return storeService.get(id, authentication);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update store", description = "Updates store geography for future use only. Historical sales, receipts, tax records, and reports retain their original snapshots.")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_UPDATE) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    StoreResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody StoreUpdateRequest request,
            Authentication authentication) {
        return storeService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update store status")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_UPDATE) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    StoreResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StoreStatusRequest request,
            Authentication authentication) {
        return storeService.updateStatus(id, request, authentication);
    }
}
