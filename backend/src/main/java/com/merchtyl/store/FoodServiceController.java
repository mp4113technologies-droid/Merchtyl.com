package com.merchtyl.store;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/food-service")
public class FoodServiceController {
    private final StoreCapabilityService capabilityService;
    private final StoreRepository storeRepository;
    private final com.merchtyl.security.StoreAccessService storeAccessService;

    public FoodServiceController(StoreCapabilityService capabilityService, StoreRepository storeRepository,
            com.merchtyl.security.StoreAccessService storeAccessService) {
        this.capabilityService = capabilityService;
        this.storeRepository = storeRepository;
        this.storeAccessService = storeAccessService;
    }

    @GetMapping("/configuration")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).FOOD_POS_ACCESS)")
    FoodServiceConfigurationResponse configuration(@PathVariable UUID storeId, Authentication authentication) {
        return FoodServiceConfigurationResponse.from(
                capabilityService.requireCapability(storeId, StoreCapability.FOOD_SERVICE, authentication));
    }

    @PutMapping("/configuration")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_UPDATE)")
    @org.springframework.transaction.annotation.Transactional
    FoodServiceConfigurationResponse updateConfiguration(@PathVariable UUID storeId,
            @Valid @RequestBody FoodServiceConfigurationRequest request, Authentication authentication) {
        storeAccessService.requireStoreManagement(authentication, storeId);
        Store store = capabilityService.requireCapability(storeId, StoreCapability.FOOD_SERVICE, authentication);
        store.configureKitchenPrinting(request.autoPrintAsapKitchenTickets(),
                request.autoPrintScheduledKitchenTickets(), request.scheduledKitchenPrintLeadMinutes());
        return FoodServiceConfigurationResponse.from(storeRepository.save(store));
    }
}
