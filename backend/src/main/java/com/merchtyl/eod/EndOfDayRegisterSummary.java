package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.security.User;
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
import java.time.Instant;

@Entity
@Table(name = "end_of_day_register_summaries")
public class EndOfDayRegisterSummary extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_register_summaries_report"))
    private EndOfDayReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_session_id", updatable = false, foreignKey = @ForeignKey(name = "fk_eod_register_summaries_session"))
    private RegisterSession registerSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_register_summaries_register"))
    private Register register;

    @Column(nullable = false, updatable = false, length = 64)
    private String registerCode;

    @Column(nullable = false, updatable = false, length = 180)
    private String registerName;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal openingFloat;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cashReceipts;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal changeGiven;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cashRefunds;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lotteryCashSales;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lotteryPayouts;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lotteryPayoutReversals;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lotterySaleCancellations;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cashIn;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cashOut;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal safeDrops;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal floatAdditions;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal floatRemovals;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal expenses;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal closingAdjustments;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal variance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_register_summaries_opened_by"))
    private User openedBy;

    @Column(nullable = false, updatable = false, length = 180)
    private String openedByName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by", updatable = false, foreignKey = @ForeignKey(name = "fk_eod_register_summaries_closed_by"))
    private User closedBy;

    @Column(updatable = false, length = 180)
    private String closedByName;

    @Column(nullable = false, updatable = false)
    private Instant openedAt;

    @Column(updatable = false)
    private Instant closedAt;

    @Column(nullable = false, updatable = false)
    private boolean forceClosed;

    @Column(updatable = false, length = 1000)
    private String forceCloseReason;

    protected EndOfDayRegisterSummary() {
    }

    public EndOfDayRegisterSummary(EndOfDayReport report, RegisterSession session, RegisterSummaryValues values) {
        this.report = report;
        this.registerSession = session;
        this.register = session.getRegister();
        this.registerCode = session.getRegister().getCode();
        this.registerName = session.getRegister().getName();
        this.openingFloat = values.openingFloat();
        this.cashReceipts = values.cashReceipts();
        this.changeGiven = values.changeGiven();
        this.cashRefunds = values.cashRefunds();
        this.lotteryCashSales = values.lotteryCashSales();
        this.lotteryPayouts = values.lotteryPayouts();
        this.lotteryPayoutReversals = values.lotteryPayoutReversals();
        this.lotterySaleCancellations = values.lotterySaleCancellations();
        this.cashIn = values.cashIn();
        this.cashOut = values.cashOut();
        this.safeDrops = values.safeDrops();
        this.floatAdditions = values.floatAdditions();
        this.floatRemovals = values.floatRemovals();
        this.expenses = values.expenses();
        this.closingAdjustments = values.closingAdjustments();
        this.expectedCash = values.expectedCash();
        this.countedCash = values.countedCash();
        this.variance = values.variance();
        this.openedBy = session.getAssignedCashier();
        this.openedByName = displayName(session.getAssignedCashier());
        this.closedBy = session.getClosedBy();
        this.closedByName = session.getClosedBy() == null ? null : displayName(session.getClosedBy());
        this.openedAt = session.getOpenedAt();
        this.closedAt = session.getClosedAt();
        this.forceClosed = session.getStatus().name().equals("FORCE_CLOSED");
        this.forceCloseReason = session.getForceCloseReason();
        initializeIdAndTimestamps();
    }

    private static String displayName(User user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getEmail() : user.getDisplayName();
    }

    public EndOfDayReport getReport() { return report; }
    public RegisterSession getRegisterSession() { return registerSession; }
    public Register getRegister() { return register; }
    public String getRegisterCode() { return registerCode; }
    public String getRegisterName() { return registerName; }
    public BigDecimal getOpeningFloat() { return openingFloat; }
    public BigDecimal getCashReceipts() { return cashReceipts; }
    public BigDecimal getChangeGiven() { return changeGiven; }
    public BigDecimal getCashRefunds() { return cashRefunds; }
    public BigDecimal getLotteryCashSales() { return lotteryCashSales; }
    public BigDecimal getLotteryPayouts() { return lotteryPayouts; }
    public BigDecimal getLotteryPayoutReversals() { return lotteryPayoutReversals; }
    public BigDecimal getLotterySaleCancellations() { return lotterySaleCancellations; }
    public BigDecimal getCashIn() { return cashIn; }
    public BigDecimal getCashOut() { return cashOut; }
    public BigDecimal getSafeDrops() { return safeDrops; }
    public BigDecimal getFloatAdditions() { return floatAdditions; }
    public BigDecimal getFloatRemovals() { return floatRemovals; }
    public BigDecimal getExpenses() { return expenses; }
    public BigDecimal getClosingAdjustments() { return closingAdjustments; }
    public BigDecimal getExpectedCash() { return expectedCash; }
    public BigDecimal getCountedCash() { return countedCash; }
    public BigDecimal getVariance() { return variance; }
    public User getOpenedBy() { return openedBy; }
    public String getOpenedByName() { return openedByName; }
    public User getClosedBy() { return closedBy; }
    public String getClosedByName() { return closedByName; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public boolean isForceClosed() { return forceClosed; }
    public String getForceCloseReason() { return forceCloseReason; }
}
