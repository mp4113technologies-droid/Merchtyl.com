package com.merchtyl.register;

import com.merchtyl.store.Store;

record RegisterValues(
        Store store,
        String code,
        String name,
        String locationDescription,
        boolean active
) {
}
