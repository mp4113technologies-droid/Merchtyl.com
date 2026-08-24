package com.merchtyl.device;

import java.util.UUID;

public record DeviceSearchRequest(
        UUID storeId,
        UUID registerId,
        String deviceIdentifier,
        String displayName,
        String deviceType,
        Boolean active,
        int page,
        int size
) {
}
