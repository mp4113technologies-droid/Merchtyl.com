package com.merchtyl.lottery;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LotteryPayoutValidationRequest(
        @NotNull Long version,
        LotteryVerificationState ticketValidationState,
        LotteryVerificationState ageVerificationState,
        LotteryVerificationState identificationVerificationState,
        @Size(max = 180) String validationReference
) {
}
