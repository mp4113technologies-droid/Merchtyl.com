package com.merchtyl.registersession;

import com.merchtyl.device.Device;
import com.merchtyl.eod.BusinessDay;
import com.merchtyl.eod.BusinessDayStatus;
import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "register_sessions")
public class RegisterSession extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false)
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_day_id")
    private BusinessDay businessDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_cashier_id", nullable = false)
    private User assignedCashier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by_user_id")
    private User openedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RegisterSessionStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal openingCash;

    @Column(nullable = false)
    private Instant openedAt;

    @Column(precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(precision = 12, scale = 2)
    private BigDecimal expectedCashAtClose;

    @Column(precision = 12, scale = 2)
    private BigDecimal differenceCash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

    @Column
    private Instant closedAt;

    @Column(length = 1000)
    private String forceCloseReason;

    protected RegisterSession() {
    }

    RegisterSession(
            Store store,
            Register register,
            BusinessDay businessDay,
            Device device,
            User assignedCashier,
            BigDecimal openingCash,
            Instant openedAt) {
        this.store = store;
        this.register = register;
        this.businessDay = businessDay;
        this.device = device;
        this.assignedCashier = assignedCashier;
        this.openedBy = assignedCashier;
        this.status = RegisterSessionStatus.OPEN;
        this.openingCash = openingCash;
        this.openedAt = openedAt;
        initializeIdAndTimestamps();
    }

    RegisterSession(
            Store store,
            Register register,
            Device device,
            User assignedCashier,
            BigDecimal openingCash,
            Instant openedAt) {
        this(store, register, null, device, assignedCashier, openingCash, openedAt);
    }

    public Store getStore() {
        return store;
    }

    public Register getRegister() {
        return register;
    }

    public BusinessDay getBusinessDay() {
        return businessDay;
    }

    public boolean isBusinessDayOperational() {
        return businessDay == null
                || businessDay.getStatus() == BusinessDayStatus.OPEN
                || businessDay.getStatus() == BusinessDayStatus.REOPENED;
    }

    public Device getDevice() {
        return device;
    }

    public User getAssignedCashier() {
        return assignedCashier;
    }

    public User getOpenedBy() {
        return openedBy;
    }

    void transferTo(User operator) {
        this.assignedCashier = operator;
    }

    public RegisterSessionStatus getStatus() {
        return status;
    }

    public BigDecimal getOpeningCash() {
        return openingCash;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public BigDecimal getCountedCash() {
        return countedCash;
    }

    public BigDecimal getExpectedCashAtClose() {
        return expectedCashAtClose;
    }

    public BigDecimal getDifferenceCash() {
        return differenceCash;
    }

    public User getClosedBy() {
        return closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getForceCloseReason() {
        return forceCloseReason;
    }

    void close(BigDecimal countedCash, BigDecimal expectedCash, User closedBy, Instant closedAt) {
        this.status = RegisterSessionStatus.CLOSED;
        this.countedCash = countedCash;
        this.expectedCashAtClose = expectedCash;
        this.differenceCash = countedCash.subtract(expectedCash);
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.forceCloseReason = null;
    }

    void forceClose(BigDecimal countedCash, BigDecimal expectedCash, User closedBy, Instant closedAt, String forceCloseReason) {
        this.status = RegisterSessionStatus.FORCE_CLOSED;
        this.countedCash = countedCash;
        this.expectedCashAtClose = expectedCash;
        this.differenceCash = countedCash.subtract(expectedCash);
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.forceCloseReason = forceCloseReason;
    }
}
