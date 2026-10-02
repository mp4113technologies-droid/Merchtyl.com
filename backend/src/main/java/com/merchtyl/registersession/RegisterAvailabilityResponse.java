package com.merchtyl.registersession;

import com.merchtyl.register.RegisterType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RegisterAvailabilityResponse(
        UUID registerId,
        RegisterType registerType,
        String state,
        UUID sessionId,
        String operatorDisplayName,
        Instant openedAt,
        Long version,
        BigDecimal openingCash,
        String openingCashSource,
        Boolean firstRegisterOpeningForBusinessDay
) {
    public RegisterAvailabilityResponse(
            UUID registerId, RegisterType registerType, String state, UUID sessionId,
            String operatorDisplayName, Instant openedAt, Long version,
            BigDecimal openingCash, String openingCashSource) {
        this(registerId, registerType, state, sessionId, operatorDisplayName, openedAt, version,
                openingCash, openingCashSource, null);
    }

    static RegisterAvailabilityResponse available(UUID registerId, RegisterType registerType,
                                                   BigDecimal openingCash, String openingCashSource,
                                                   boolean firstRegisterOpeningForBusinessDay) {
        return new RegisterAvailabilityResponse(registerId, registerType, "AVAILABLE", null, null, null, null,
                openingCash, openingCashSource, firstRegisterOpeningForBusinessDay);
    }
}
