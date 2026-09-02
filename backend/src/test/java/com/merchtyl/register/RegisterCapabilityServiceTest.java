package com.merchtyl.register;

import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.platform.billing.CommercialCapability;
import com.merchtyl.platform.billing.SubscriptionEntitlementService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreCapability;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterCapabilityServiceTest {
    private final SubscriptionEntitlementService entitlements = mock(SubscriptionEntitlementService.class);
    private final RegisterCapabilityService service = new RegisterCapabilityService(entitlements);

    @Test
    void foodServiceStoreAllowsRestaurantRegisterWhenSubscriptionIsEnabled() {
        Store store = store(Set.of(StoreCapability.FOOD_SERVICE));

        service.requireEnabled(store, RegisterType.FOOD_SERVICE);

        verify(entitlements).requireActive(store.getTenantId(), CommercialCapability.FOOD_SERVICE);
    }

    @Test
    void retailOnlyStoreRejectsRestaurantRegisterWithSpecificError() {
        Store store = store(Set.of(StoreCapability.RETAIL));

        assertThatThrownBy(() -> service.requireEnabled(store, RegisterType.FOOD_SERVICE))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("STORE_CAPABILITY_NOT_ENABLED: Food service is not enabled for this store.");
        verify(entitlements, never()).requireActive(store.getTenantId(), CommercialCapability.FOOD_SERVICE);
    }

    @Test
    void mixedStoreAllowsRetailAndRestaurantRegisters() {
        Store store = store(Set.of(StoreCapability.RETAIL, StoreCapability.FOOD_SERVICE));

        service.requireEnabled(store, RegisterType.RETAIL);
        service.requireEnabled(store, RegisterType.FOOD_SERVICE);

        verify(entitlements).requireActive(store.getTenantId(), CommercialCapability.FOOD_SERVICE);
    }

    @Test
    void subscriptionDenialIsNotBypassedForFoodServiceStore() {
        Store store = store(Set.of(StoreCapability.FOOD_SERVICE));
        UUID tenantId = store.getTenantId();
        org.mockito.Mockito.doThrow(new ForbiddenOperationException("MERCHANT_CAPABILITY_NOT_ENABLED"))
                .when(entitlements).requireActive(tenantId, CommercialCapability.FOOD_SERVICE);

        assertThatThrownBy(() -> service.requireEnabled(store, RegisterType.FOOD_SERVICE))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("MERCHANT_CAPABILITY_NOT_ENABLED");
    }

    private Store store(Set<StoreCapability> capabilities) {
        Store store = mock(Store.class);
        when(store.getTenantId()).thenReturn(UUID.randomUUID());
        when(store.getCapabilities()).thenReturn(capabilities);
        return store;
    }
}
