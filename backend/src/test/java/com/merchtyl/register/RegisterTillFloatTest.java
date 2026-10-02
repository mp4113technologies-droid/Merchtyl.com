package com.merchtyl.register;

import com.merchtyl.store.Store;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisterTillFloatTest {
    @Test
    void storeDefaultIsEffectiveWhenRegisterHasNoOverride() {
        Store store = storeWithDefault("440.00");
        Register register = register(store);

        assertThat(register.getTillFloatOverride()).isNull();
        assertThat(register.getEffectiveTillFloat()).isEqualByComparingTo("440.00");
    }

    @Test
    void registerOverrideWinsAndRemovingItFallsBackToStore() {
        Store store = storeWithDefault("440.00");
        Register register = register(store);

        register.configureTillFloatOverride(new BigDecimal("300.00"));
        assertThat(register.getEffectiveTillFloat()).isEqualByComparingTo("300.00");

        register.configureTillFloatOverride(null);
        assertThat(register.getEffectiveTillFloat()).isEqualByComparingTo("440.00");
    }

    @Test
    void twoRegistersCanResolveDifferentEffectiveFloats() {
        Store store = storeWithDefault("440.00");
        Register first = register(store);
        Register second = register(store);
        second.configureTillFloatOverride(new BigDecimal("300.00"));

        assertThat(first.getEffectiveTillFloat()).isEqualByComparingTo("440.00");
        assertThat(second.getEffectiveTillFloat()).isEqualByComparingTo("300.00");
    }

    @Test
    void unconfiguredStoreDoesNotInventAnEffectiveFloat() {
        Store store = storeWithDefault(null);

        assertThat(register(store).getEffectiveTillFloat()).isNull();
    }

    private static Store storeWithDefault(String amount) {
        Store store = mock(Store.class);
        when(store.getDefaultTillFloat()).thenReturn(amount == null ? null : new BigDecimal(amount));
        return store;
    }

    private static Register register(Store store) {
        return new Register(store, "R1", "Register 1", null, true, RegisterType.RETAIL);
    }
}
