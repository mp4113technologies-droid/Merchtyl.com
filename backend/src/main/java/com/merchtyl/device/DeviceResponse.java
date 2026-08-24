package com.merchtyl.device;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        UUID storeId,
        UUID registerId,
        String deviceIdentifier,
        String displayName,
        String deviceType,
        Instant registeredAt,
        Instant lastSeenAt,
        boolean active,
        long version
) {
    static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getStore().getId(),
                device.getRegister().getId(),
                device.getDeviceIdentifier(),
                device.getDisplayName(),
                device.getDeviceType(),
                device.getRegisteredAt(),
                device.getLastSeenAt(),
                device.isActive(),
                device.getVersion());
    }
}
