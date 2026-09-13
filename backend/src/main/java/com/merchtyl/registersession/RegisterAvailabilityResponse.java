package com.merchtyl.registersession;

import com.merchtyl.register.RegisterType;

import java.time.Instant;
import java.util.UUID;

public record RegisterAvailabilityResponse(
        UUID registerId,
        RegisterType registerType,
        String state,
        UUID sessionId,
        String operatorDisplayName,
        Instant openedAt,
        Long version
) {
    static RegisterAvailabilityResponse available(UUID registerId, RegisterType registerType) {
        return new RegisterAvailabilityResponse(registerId, registerType, "AVAILABLE", null, null, null, null);
    }
}
