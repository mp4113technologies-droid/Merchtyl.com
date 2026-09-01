package com.merchtyl.register;

import com.merchtyl.audit.AuditService;
import com.merchtyl.common.ConflictException;
import com.merchtyl.platform.billing.CommercialCapability;
import com.merchtyl.platform.billing.SubscriptionEntitlementService;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreCapability;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegisterTypeServiceTest {
    private final RegisterRepository registers = mock(RegisterRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final SubscriptionEntitlementService entitlements = mock(SubscriptionEntitlementService.class);
    private final RegisterService service = new RegisterService(registers, stores, mock(UserRepository.class),
            mock(AuditService.class), entitlements, mock(JdbcTemplate.class));

    @Test void retailStoreAllowsRetailRegister() { assertCreated(Set.of(StoreCapability.RETAIL), RegisterType.RETAIL); }

    @Test void retailStoreRejectsFoodRegister() {
        stubStore(Set.of(StoreCapability.RETAIL));
        assertThatThrownBy(() -> service.create(request(RegisterType.FOOD_SERVICE), null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("REGISTER_TYPE_NOT_SUPPORTED_BY_STORE");
        verify(registers, never()).saveAndFlush(any());
    }

    @Test void foodOnlyStoreAllowsFoodRegisterWhenEntitled() {
        assertCreated(Set.of(StoreCapability.FOOD_SERVICE), RegisterType.FOOD_SERVICE);
        verify(entitlements).requireActive(any(), eq(CommercialCapability.FOOD_SERVICE));
    }

    @Test void mixedStoreAllowsBothRegisterTypes() {
        assertCreated(Set.of(StoreCapability.RETAIL, StoreCapability.FOOD_SERVICE), RegisterType.RETAIL);
        reset(registers, stores, entitlements);
        assertCreated(Set.of(StoreCapability.RETAIL, StoreCapability.FOOD_SERVICE), RegisterType.FOOD_SERVICE);
    }

    private void assertCreated(Set<StoreCapability> capabilities, RegisterType type) {
        stubStore(capabilities);
        when(registers.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RegisterResponse response = service.create(request(type), null);
        assertThat(response.type()).isEqualTo(type);
    }

    private void stubStore(Set<StoreCapability> capabilities) {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(UUID.randomUUID());
        when(store.getTenantId()).thenReturn(UUID.randomUUID());
        when(store.getCapabilities()).thenReturn(capabilities);
        when(stores.findById(any())).thenReturn(Optional.of(store));
        when(registers.existsByStore_IdAndCodeIgnoreCase(any(), any())).thenReturn(false);
    }

    private RegisterRequest request(RegisterType type) {
        return new RegisterRequest(UUID.randomUUID(), "COUNTER", "Counter", null, true, type);
    }
}
