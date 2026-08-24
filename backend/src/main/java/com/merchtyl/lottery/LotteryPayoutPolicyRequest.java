package com.merchtyl.lottery;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LotteryPayoutPolicyRequest(
        @NotNull UUID operatorId,
        @NotNull UUID jurisdictionId,
        @NotNull UUID storeId,
        @NotNull @DecimalMin("0.00") BigDecimal maximumCashPayout,
        @NotNull @DecimalMin("0.00") BigDecimal cashierApprovalLimit,
        @NotNull @DecimalMin("0.00") BigDecimal managerApprovalThreshold,
        @NotNull @DecimalMin("0.00") BigDecimal operatorReferralThreshold,
        @NotNull @DecimalMin("0.00") BigDecimal protectedRegisterFloat,
        boolean allowCashPayout,
        boolean allowStoreCredit,
        boolean requireTicketValidation,
        boolean requireAgeVerification,
        boolean requireCustomerIdentification,
        boolean allowAlternateRegister,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotNull LotteryPayoutPolicyStatus status
) {
}
