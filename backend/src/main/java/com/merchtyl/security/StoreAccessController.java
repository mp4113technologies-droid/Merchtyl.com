package com.merchtyl.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/store-access")
public class StoreAccessController {
    private final StoreAccessService storeAccessService;

    public StoreAccessController(StoreAccessService storeAccessService) {
        this.storeAccessService = storeAccessService;
    }

    @GetMapping("/assigned-stores")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_ACCESS)")
    List<AssignedStoreResponse> assignedStores(Authentication authentication) {
        return storeAccessService.assignedStores(authentication);
    }

    @GetMapping("/stores/{storeId}/validate")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_ACCESS)")
    AssignedStoreResponse validate(@PathVariable UUID storeId, Authentication authentication) {
        storeAccessService.requireStoreAccess(authentication, storeId);
        return storeAccessService.assignedStores(authentication).stream()
                .filter(store -> store.storeId().equals(storeId))
                .findFirst()
                .orElseThrow();
    }
}
