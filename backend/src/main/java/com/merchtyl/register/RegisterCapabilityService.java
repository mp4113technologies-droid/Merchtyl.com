package com.merchtyl.register;

import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.platform.billing.CommercialCapability;
import com.merchtyl.platform.billing.SubscriptionEntitlementService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreCapability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterCapabilityService {
    private final SubscriptionEntitlementService entitlements;

    public RegisterCapabilityService(SubscriptionEntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    @Transactional(readOnly = true)
    public void requireEnabled(Store store, RegisterType registerType) {
        StoreCapability required = registerType == RegisterType.RETAIL
                ? StoreCapability.RETAIL : StoreCapability.FOOD_SERVICE;
        if (!store.getCapabilities().contains(required)) {
            String message = required == StoreCapability.FOOD_SERVICE
                    ? "Food service is not enabled for this store."
                    : "Retail is not enabled for this store.";
            throw new ForbiddenOperationException("STORE_CAPABILITY_NOT_ENABLED: " + message);
        }
        if (required == StoreCapability.FOOD_SERVICE) {
            entitlements.requireActive(store.getTenantId(), CommercialCapability.FOOD_SERVICE);
        }
    }
}
