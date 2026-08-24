package com.merchtyl.device;

import com.merchtyl.register.Register;
import com.merchtyl.store.Store;

record DeviceValues(
        Store store,
        Register register,
        String deviceIdentifier,
        String displayName,
        String deviceType,
        boolean active
) {
}
