package com.merchtyl.registersession;

import com.merchtyl.eod.BusinessDay;
import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/** Physical drawer state for one register during one store business day. */
@Entity
@Table(name = "register_business_day_cash_states", uniqueConstraints =
        @UniqueConstraint(name = "uq_register_day_cash_state", columnNames = {"store_id", "register_id", "business_day_id"}))
public class RegisterBusinessDayCashState extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_register_day_cash_store"))
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, foreignKey = @ForeignKey(name = "fk_register_day_cash_register"))
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_day_id", nullable = false, foreignKey = @ForeignKey(name = "fk_register_day_cash_business_day"))
    private BusinessDay businessDay;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal initialFloat;

    @Column(precision = 12, scale = 2)
    private BigDecimal targetFloat;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal retainedCash;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashRemoved;

    @Column(precision = 12, scale = 2)
    private BigDecimal finalExpectedCash;

    @Column(precision = 12, scale = 2)
    private BigDecimal finalCountedCash;

    @Column(nullable = false)
    private int sessionCount;

    protected RegisterBusinessDayCashState() {}

    public RegisterBusinessDayCashState(Store store, Register register, BusinessDay businessDay, BigDecimal initialFloat) {
        this(store, register, businessDay, initialFloat, initialFloat);
    }

    public RegisterBusinessDayCashState(Store store, Register register, BusinessDay businessDay,
                                        BigDecimal initialFloat, BigDecimal targetFloat) {
        this.store = store;
        this.register = register;
        this.businessDay = businessDay;
        this.initialFloat = initialFloat;
        this.targetFloat = targetFloat;
        this.retainedCash = initialFloat;
        this.cashRemoved = BigDecimal.ZERO.setScale(2);
        this.sessionCount = 1;
        initializeIdAndTimestamps();
    }

    public void openHandoff(BigDecimal openingCash) {
        if (retainedCash.compareTo(openingCash) != 0) {
            throw new IllegalArgumentException("Session opening balance must equal retained till cash");
        }
        sessionCount++;
    }

    public void settle(BigDecimal expectedCash, BigDecimal countedCash, BigDecimal retainedCash) {
        if (retainedCash.signum() < 0 || retainedCash.compareTo(countedCash) > 0) {
            throw new IllegalArgumentException("Retained cash must be between zero and counted cash");
        }
        this.finalExpectedCash = expectedCash;
        this.finalCountedCash = countedCash;
        this.cashRemoved = this.cashRemoved.add(countedCash.subtract(retainedCash));
        this.retainedCash = retainedCash;
    }

    public Store getStore() { return store; }
    public Register getRegister() { return register; }
    public BusinessDay getBusinessDay() { return businessDay; }
    public BigDecimal getInitialFloat() { return initialFloat; }
    public BigDecimal getTargetFloat() { return targetFloat; }
    public BigDecimal getRetainedCash() { return retainedCash; }
    public BigDecimal getCashRemoved() { return cashRemoved; }
    public BigDecimal getFinalExpectedCash() { return finalExpectedCash; }
    public BigDecimal getFinalCountedCash() { return finalCountedCash; }
    public int getSessionCount() { return sessionCount; }
}
