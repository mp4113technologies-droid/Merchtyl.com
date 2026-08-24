package com.merchtyl.lottery;

import com.merchtyl.device.Device;
import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "lottery_payouts")
public class LotteryPayout extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payouts_operator"))
    private LotteryOperator operator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payouts_policy"))
    private LotteryPayoutPolicy policy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payouts_store"))
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payouts_register"))
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payouts_device"))
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_payouts_cashier"))
    private User cashier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_session_id", foreignKey = @ForeignKey(name = "fk_lottery_payouts_register_session"))
    private RegisterSession registerSession;

    @Column(nullable = false, length = 180)
    private String ticketNumber;

    @Column(length = 180)
    private String validationReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotteryPayoutMethod payoutMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotteryPayoutStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotteryVerificationState ticketValidationState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotteryVerificationState ageVerificationState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotteryVerificationState identificationVerificationState;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashierApprovalLimit;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal managerApprovalThreshold;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal operatorReferralThreshold;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal maximumCashPayout;

    @Column(nullable = false)
    private boolean ticketValidationRequired;

    @Column(nullable = false)
    private boolean ageVerificationRequired;

    @Column(nullable = false)
    private boolean identificationRequired;

    @Column(nullable = false)
    private boolean alternateRegisterAllowed;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column
    private Instant validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    @Column
    private Instant authorizedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorized_by")
    private User authorizedBy;

    @Column
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by")
    private User paidBy;

    @Column
    private Instant rejectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    @Column(length = 1000)
    private String rejectionReason;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "payout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("approvedAt ASC")
    private final List<LotteryPayoutApproval> approvals = new ArrayList<>();

    protected LotteryPayout() {
    }

    LotteryPayout(
            LotteryOperator operator,
            LotteryPayoutPolicy policy,
            Store store,
            Register register,
            Device device,
            User cashier,
            RegisterSession registerSession,
            String ticketNumber,
            BigDecimal amount,
            String currencyCode,
            LotteryPayoutMethod payoutMethod,
            LocalDate businessDate,
            Instant occurredAt,
            String notes) {
        this.operator = operator;
        this.policy = policy;
        this.store = store;
        this.register = register;
        this.device = device;
        this.cashier = cashier;
        this.registerSession = registerSession;
        this.ticketNumber = ticketNumber;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.payoutMethod = payoutMethod;
        this.status = LotteryPayoutStatus.DRAFT;
        this.ticketValidationState = policy.isRequireTicketValidation() ? LotteryVerificationState.PENDING : LotteryVerificationState.NOT_REQUIRED;
        this.ageVerificationState = policy.isRequireAgeVerification() ? LotteryVerificationState.PENDING : LotteryVerificationState.NOT_REQUIRED;
        this.identificationVerificationState = policy.isRequireCustomerIdentification() ? LotteryVerificationState.PENDING : LotteryVerificationState.NOT_REQUIRED;
        this.cashierApprovalLimit = policy.getCashierApprovalLimit();
        this.managerApprovalThreshold = policy.getManagerApprovalThreshold();
        this.operatorReferralThreshold = policy.getOperatorReferralThreshold();
        this.maximumCashPayout = policy.getMaximumCashPayout();
        this.ticketValidationRequired = policy.isRequireTicketValidation();
        this.ageVerificationRequired = policy.isRequireAgeVerification();
        this.identificationRequired = policy.isRequireCustomerIdentification();
        this.alternateRegisterAllowed = policy.isAllowAlternateRegister();
        this.businessDate = businessDate;
        this.occurredAt = occurredAt;
        this.notes = notes;
        initializeIdAndTimestamps();
    }

    void validate(
            LotteryVerificationState ticketValidationState,
            LotteryVerificationState ageVerificationState,
            LotteryVerificationState identificationVerificationState,
            String validationReference,
            User validatedBy,
            Instant validatedAt,
            LotteryPayoutStatus resultingStatus) {
        this.ticketValidationState = ticketValidationState;
        this.ageVerificationState = ageVerificationState;
        this.identificationVerificationState = identificationVerificationState;
        this.validationReference = validationReference;
        this.validatedBy = validatedBy;
        this.validatedAt = validatedAt;
        this.status = resultingStatus;
    }

    void authorize(User authorizedBy, Instant authorizedAt, LotteryPayoutApprovalType approvalType, BigDecimal thresholdAmount, String notes) {
        this.authorizedBy = authorizedBy;
        this.authorizedAt = authorizedAt;
        this.status = LotteryPayoutStatus.AUTHORIZED;
        approvals.add(new LotteryPayoutApproval(this, approvalType, authorizedBy, authorizedAt, amount, thresholdAmount, notes));
    }

    void addReferralApproval(User approvedBy, Instant approvedAt, String notes) {
        approvals.add(new LotteryPayoutApproval(this, LotteryPayoutApprovalType.OPERATOR_REFERRAL, approvedBy, approvedAt, amount, operatorReferralThreshold, notes));
    }

    void reject(User rejectedBy, Instant rejectedAt, String reason) {
        this.rejectedBy = rejectedBy;
        this.rejectedAt = rejectedAt;
        this.rejectionReason = reason;
        this.status = LotteryPayoutStatus.REJECTED;
    }

    void completeCash(User paidBy, Instant paidAt) {
        this.paidBy = paidBy;
        this.paidAt = paidAt;
        this.status = LotteryPayoutStatus.PAID;
    }

    void reverse() {
        this.status = LotteryPayoutStatus.REVERSED;
    }

    public LotteryOperator getOperator() {
        return operator;
    }

    public LotteryPayoutPolicy getPolicy() {
        return policy;
    }

    public Store getStore() {
        return store;
    }

    public Register getRegister() {
        return register;
    }

    public Device getDevice() {
        return device;
    }

    public User getCashier() {
        return cashier;
    }

    public RegisterSession getRegisterSession() {
        return registerSession;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getValidationReference() {
        return validationReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LotteryPayoutMethod getPayoutMethod() {
        return payoutMethod;
    }

    public LotteryPayoutStatus getStatus() {
        return status;
    }

    public LotteryVerificationState getTicketValidationState() {
        return ticketValidationState;
    }

    public LotteryVerificationState getAgeVerificationState() {
        return ageVerificationState;
    }

    public LotteryVerificationState getIdentificationVerificationState() {
        return identificationVerificationState;
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

    public BigDecimal getMaximumCashPayout() {
        return maximumCashPayout;
    }

    public boolean isTicketValidationRequired() {
        return ticketValidationRequired;
    }

    public boolean isAgeVerificationRequired() {
        return ageVerificationRequired;
    }

    public boolean isIdentificationRequired() {
        return identificationRequired;
    }

    public boolean isAlternateRegisterAllowed() {
        return alternateRegisterAllowed;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public User getValidatedBy() {
        return validatedBy;
    }

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public User getAuthorizedBy() {
        return authorizedBy;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public User getPaidBy() {
        return paidBy;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public User getRejectedBy() {
        return rejectedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getNotes() {
        return notes;
    }

    public List<LotteryPayoutApproval> getApprovals() {
        return Collections.unmodifiableList(approvals);
    }
}
