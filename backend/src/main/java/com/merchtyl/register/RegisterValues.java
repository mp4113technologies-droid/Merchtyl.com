package com.merchtyl.register;

import com.merchtyl.store.Store;
import java.math.BigDecimal;

record RegisterValues(
        Store store,
        String code,
        String name,
        String locationDescription,
        boolean active,
        RegisterType type,
        BigDecimal tillFloatOverride
) {
}
