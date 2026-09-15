package com.merchtyl.store;

import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.register.RegisterCapabilityService;
import com.merchtyl.register.RegisterType;
import com.merchtyl.platform.billing.CommercialCapability;
import com.merchtyl.platform.billing.SubscriptionEntitlementService;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.security.AuthorizationService;
import com.merchtyl.security.PermissionCode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StoreCapabilityService {
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final RegisterCapabilityService registerCapabilities;
    private final SubscriptionEntitlementService subscriptionEntitlements;
    private final FeatureService features;
    private final AuthorizationService authorization;

    public StoreCapabilityService(StoreRepository storeRepository, StoreAccessService storeAccessService,
                                  RegisterCapabilityService registerCapabilities,
                                  SubscriptionEntitlementService subscriptionEntitlements,
                                  FeatureService features,
                                  AuthorizationService authorization) {
        this.storeRepository = storeRepository;
        this.storeAccessService = storeAccessService;
        this.registerCapabilities = registerCapabilities;
        this.subscriptionEntitlements = subscriptionEntitlements;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Store requireCapability(UUID storeId, StoreCapability capability, Authentication authentication) {
        storeAccessService.requireStoreAccess(authentication, storeId);
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new NotFoundException("Store not found"));
        if (capability == StoreCapability.FOOD_SERVICE) registerCapabilities.requireEnabled(store, RegisterType.FOOD_SERVICE);
        else if (!store.getCapabilities().contains(capability))
            throw new ForbiddenOperationException("STORE_CAPABILITY_NOT_ENABLED: " + capability.name() + " is not enabled for this store.");
        return store;
    }

    @Transactional(readOnly = true)
    public EffectiveStoreCapabilityResponse effective(UUID storeId, StoreCapability capability, Authentication authentication) {
        storeAccessService.requireStoreAccess(authentication, storeId);
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new NotFoundException("Store not found"));
        boolean storeEnabled = store.getCapabilities().contains(capability);
        boolean subscriptionEnabled = switch (capability) {
            case LOTTERY -> subscriptionEntitlements.isActive(store.getTenantId(), CommercialCapability.LOTTERY);
            case FOOD_SERVICE -> subscriptionEntitlements.isActive(store.getTenantId(), CommercialCapability.FOOD_SERVICE);
            default -> true;
        };
        boolean deploymentEnabled = capability != StoreCapability.LOTTERY
                || features.isEnabled(FeatureCode.LOTTERY_SALES, storeId, null);
        boolean userAuthorized = capability != StoreCapability.LOTTERY || authorization.hasAnyPermission(authentication,
                PermissionCode.LOTTERY_VIEW,
                PermissionCode.LOTTERY_MANAGE,
                PermissionCode.LOTTERY_SALE_RECORD,
                PermissionCode.LOTTERY_PAYOUT_RECORD);
        return new EffectiveStoreCapabilityResponse(capability, subscriptionEnabled, storeEnabled, deploymentEnabled,
                userAuthorized, subscriptionEnabled && storeEnabled && deploymentEnabled && userAuthorized);
    }
}
