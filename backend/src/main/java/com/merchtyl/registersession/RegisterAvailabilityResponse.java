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
        Boolean firstRegisterOpeningForBusinessDay,
        BigDecimal targetTillAmount,
        BigDecimal currentTillAmount,
        BigDecimal restoreRequiredAmount
) {
    public RegisterAvailabilityResponse(
            UUID registerId, RegisterType registerType, String state, UUID sessionId,
            String operatorDisplayName, Instant openedAt, Long version,
            BigDecimal openingCash, String openingCashSource) {
        this(registerId, registerType, state, sessionId, operatorDisplayName, openedAt, version,
                openingCash, openingCashSource, null, null, null, null);
    }

    static RegisterAvailabilityResponse available(UUID registerId, RegisterType registerType,
                                                   BigDecimal openingCash, String openingCashSource,
                                                   boolean firstRegisterOpeningForBusinessDay) {
        return new RegisterAvailabilityResponse(registerId, registerType, "AVAILABLE", null, null, null, null,
                openingCash, openingCashSource, firstRegisterOpeningForBusinessDay,
                openingCash, openingCash, BigDecimal.ZERO.setScale(2));
    }

    static RegisterAvailabilityResponse restoreRequired(UUID registerId, RegisterType registerType,
                                                        BigDecimal target, BigDecimal current,
                                                        String openingCashSource) {
        return new RegisterAvailabilityResponse(registerId, registerType, "RESTORE_REQUIRED", null, null, null, null,
                target, openingCashSource, false, target, current,
                target.subtract(current).max(BigDecimal.ZERO).setScale(2));
    }
}
