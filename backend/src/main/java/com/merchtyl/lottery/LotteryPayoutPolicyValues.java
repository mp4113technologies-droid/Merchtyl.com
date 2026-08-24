package com.merchtyl.lottery;

import com.merchtyl.store.Store;
import com.merchtyl.tax.TaxJurisdiction;

import java.math.BigDecimal;
import java.time.LocalDate;

record LotteryPayoutPolicyValues(
        LotteryOperator operator,
        TaxJurisdiction jurisdiction,
        Store store,
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
        LotteryPayoutPolicyStatus status
) {
}
