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
        RegisterType type,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public RegisterResponse(UUID id, UUID storeId, String code, String name, String locationDescription,
                            boolean active, Instant createdAt, Instant updatedAt, long version) {
        this(id, storeId, code, name, locationDescription, active, RegisterType.RETAIL, createdAt, updatedAt, version);
    }

    static RegisterResponse from(Register register) {
        return new RegisterResponse(
                register.getId(),
                register.getStore().getId(),
                register.getCode(),
                register.getName(),
                register.getLocationDescription(),
                register.isActive(),
                register.getType(),
                register.getCreatedAt(),
                register.getUpdatedAt(),
                register.getVersion());
    }
}
