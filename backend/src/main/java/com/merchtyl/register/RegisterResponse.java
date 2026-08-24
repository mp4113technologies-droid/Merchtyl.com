package com.merchtyl.register;

import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        UUID storeId,
        String code,
        String name,
        String locationDescription,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static RegisterResponse from(Register register) {
        return new RegisterResponse(
                register.getId(),
                register.getStore().getId(),
                register.getCode(),
                register.getName(),
                register.getLocationDescription(),
                register.isActive(),
                register.getCreatedAt(),
                register.getUpdatedAt(),
                register.getVersion());
    }
}
