package com.merchtyl.lottery;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LotteryPayoutPolicyResponse(
        UUID id,
        UUID operatorId,
        String operatorCode,
        String operatorName,
        UUID jurisdictionId,
        String jurisdictionCode,
        String jurisdictionName,
        UUID storeId,
        String storeCode,
        String storeName,
        BigDecimal maximumCashPayout,
        BigDecimal cashierApprovalLimit,
        BigDecimal managerApprovalThreshold,
        BigDecimal operatorReferralThreshold,
        BigDecimal protectedRegisterFloat,
        boolean allowCashPayout,
        boolean allowStoreCredit,
        boolean requireTicketValidation,
        boolean requireAgeVerification,
        boolean requireCustomerIdentification,
        boolean allowAlternateRegister,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LotteryPayoutPolicyStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static LotteryPayoutPolicyResponse from(LotteryPayoutPolicy policy) {
        return new LotteryPayoutPolicyResponse(
                policy.getId(),
                policy.getOperator().getId(),
                policy.getOperator().getCode(),
                policy.getOperator().getName(),
                policy.getJurisdiction().getId(),
                policy.getJurisdiction().getCode(),
                policy.getJurisdiction().getName(),
                policy.getStore().getId(),
                policy.getStore().getCode(),
                policy.getStore().getName(),
                policy.getMaximumCashPayout(),
                policy.getCashierApprovalLimit(),
                policy.getManagerApprovalThreshold(),
                policy.getOperatorReferralThreshold(),
                policy.getProtectedRegisterFloat(),
                policy.isAllowCashPayout(),
                policy.isAllowStoreCredit(),
                policy.isRequireTicketValidation(),
                policy.isRequireAgeVerification(),
                policy.isRequireCustomerIdentification(),
                policy.isAllowAlternateRegister(),
                policy.getEffectiveFrom(),
                policy.getEffectiveTo(),
                policy.getStatus(),
                policy.getCreatedAt(),
                policy.getUpdatedAt(),
                policy.getVersion());
    }
}
