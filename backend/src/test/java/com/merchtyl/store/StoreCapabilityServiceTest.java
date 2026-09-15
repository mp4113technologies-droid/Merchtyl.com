package com.merchtyl.store;

import com.merchtyl.platform.billing.CommercialCapability;
import com.merchtyl.platform.billing.SubscriptionEntitlementService;
import com.merchtyl.register.RegisterCapabilityService;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.security.AuthorizationService;
import com.merchtyl.security.PermissionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreCapabilityServiceTest {
    private final StoreRepository stores = mock(StoreRepository.class);
    private final StoreAccessService access = mock(StoreAccessService.class);
    private final RegisterCapabilityService registers = mock(RegisterCapabilityService.class);
    private final SubscriptionEntitlementService entitlements = mock(SubscriptionEntitlementService.class);
    private final FeatureService features = mock(FeatureService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final UUID storeId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final Store store = mock(Store.class);
    private final StoreCapabilityService service = new StoreCapabilityService(stores, access, registers, entitlements, features, authorization);

    @BeforeEach
    void setUp() {
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getTenantId()).thenReturn(tenantId);
        when(features.isEnabled(FeatureCode.LOTTERY_SALES, storeId, null)).thenReturn(true);
        when(authorization.hasAnyPermission(authentication,
                PermissionCode.LOTTERY_VIEW,
                PermissionCode.LOTTERY_MANAGE,
                PermissionCode.LOTTERY_SALE_RECORD,
                PermissionCode.LOTTERY_PAYOUT_RECORD)).thenReturn(true);
    }

    @Test
    void lotteryIsDisabledWhenTheUserHasNoLotteryPermission() {
        when(entitlements.isActive(tenantId, CommercialCapability.LOTTERY)).thenReturn(true);
        when(store.getCapabilities()).thenReturn(Set.of(StoreCapability.LOTTERY));
        when(authorization.hasAnyPermission(authentication,
                PermissionCode.LOTTERY_VIEW,
                PermissionCode.LOTTERY_MANAGE,
                PermissionCode.LOTTERY_SALE_RECORD,
                PermissionCode.LOTTERY_PAYOUT_RECORD)).thenReturn(false);

        EffectiveStoreCapabilityResponse result = service.effective(storeId, StoreCapability.LOTTERY, authentication);

        assertThat(result.userAuthorized()).isFalse();
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void lotteryRequiresBothIncludedSubscriptionAndStoreCapability() {
        assertLottery(false, false, false);
        assertLottery(false, true, false);
        assertLottery(true, false, false);
        assertLottery(true, true, true);
    }

    private void assertLottery(boolean plan, boolean storeCapability, boolean expected) {
        when(entitlements.isActive(tenantId, CommercialCapability.LOTTERY)).thenReturn(plan);
        when(store.getCapabilities()).thenReturn(storeCapability ? Set.of(StoreCapability.LOTTERY) : Set.of());

        EffectiveStoreCapabilityResponse result = service.effective(storeId, StoreCapability.LOTTERY, authentication);

        assertThat(result.subscriptionEnabled()).isEqualTo(plan);
        assertThat(result.storeEnabled()).isEqualTo(storeCapability);
        assertThat(result.enabled()).isEqualTo(expected);
    }
}
