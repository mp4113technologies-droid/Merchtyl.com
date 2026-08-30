package com.merchtyl.store;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/food-service")
public class FoodServiceController {
    private final StoreCapabilityService capabilityService;

    public FoodServiceController(StoreCapabilityService capabilityService) { this.capabilityService = capabilityService; }

    @GetMapping("/configuration")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).FOOD_POS_ACCESS)")
    FoodServiceConfigurationResponse configuration(@PathVariable UUID storeId, Authentication authentication) {
        return FoodServiceConfigurationResponse.from(
                capabilityService.requireCapability(storeId, StoreCapability.FOOD_SERVICE, authentication));
    }
}
