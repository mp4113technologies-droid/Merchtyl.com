package com.merchtyl.lottery;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.store.Store;
import com.merchtyl.tax.TaxJurisdiction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "lottery_payout_policies")
public class LotteryPayoutPolicy extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payout_policies_operator"))
    private LotteryOperator operator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jurisdiction_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payout_policies_jurisdiction"))
    private TaxJurisdiction jurisdiction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payout_policies_store"))
    private Store store;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal maximumCashPayout;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cashierApprovalLimit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal managerApprovalThreshold;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal operatorReferralThreshold;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal protectedRegisterFloat;

    @Column(nullable = false)
    private boolean allowCashPayout;

    @Column(nullable = false)
    private boolean allowStoreCredit;

    @Column(nullable = false)
    private boolean requireTicketValidation;

    @Column(nullable = false)
    private boolean requireAgeVerification;

    @Column(nullable = false)
    private boolean requireCustomerIdentification;

    @Column(nullable = false)
    private boolean allowAlternateRegister;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LotteryPayoutPolicyStatus status;

    protected LotteryPayoutPolicy() {
    }

    LotteryPayoutPolicy(LotteryPayoutPolicyValues values) {
        update(values);
        initializeIdAndTimestamps();
    }

    void update(LotteryPayoutPolicyValues values) {
        this.operator = values.operator();
        this.jurisdiction = values.jurisdiction();
        this.store = values.store();
        this.maximumCashPayout = values.maximumCashPayout();
        this.cashierApprovalLimit = values.cashierApprovalLimit();
        this.managerApprovalThreshold = values.managerApprovalThreshold();
        this.operatorReferralThreshold = values.operatorReferralThreshold();
        this.protectedRegisterFloat = values.protectedRegisterFloat();
        this.allowCashPayout = values.allowCashPayout();
        this.allowStoreCredit = values.allowStoreCredit();
        this.requireTicketValidation = values.requireTicketValidation();
        this.requireAgeVerification = values.requireAgeVerification();
        this.requireCustomerIdentification = values.requireCustomerIdentification();
        this.allowAlternateRegister = values.allowAlternateRegister();
        this.effectiveFrom = values.effectiveFrom();
        this.effectiveTo = values.effectiveTo();
        this.status = values.status();
    }

    void setStatus(LotteryPayoutPolicyStatus status) {
        this.status = status;
    }

    public LotteryOperator getOperator() {
        return operator;
    }

    public TaxJurisdiction getJurisdiction() {
        return jurisdiction;
    }

    public Store getStore() {
        return store;
    }

    public BigDecimal getMaximumCashPayout() {
        return maximumCashPayout;
    }

    public BigDecimal getCashierApprovalLimit() {
        return cashierApprovalLimit;
    }

    public BigDecimal getManagerApprovalThreshold() {
        return managerApprovalThreshold;
    }

    public BigDecimal getOperatorReferralThreshold() {
        return operatorReferralThreshold;
    }

    public BigDecimal getProtectedRegisterFloat() {
        return protectedRegisterFloat;
    }

    public boolean isAllowCashPayout() {
        return allowCashPayout;
    }

    public boolean isAllowStoreCredit() {
        return allowStoreCredit;
    }

    public boolean isRequireTicketValidation() {
        return requireTicketValidation;
    }

    public boolean isRequireAgeVerification() {
        return requireAgeVerification;
    }

    public boolean isRequireCustomerIdentification() {
        return requireCustomerIdentification;
    }

    public boolean isAllowAlternateRegister() {
        return allowAlternateRegister;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public LotteryPayoutPolicyStatus getStatus() {
        return status;
    }
}
