package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "business_days",
        uniqueConstraints = @UniqueConstraint(name = "uq_business_days_store_date", columnNames = {"store_id", "business_date"}))
public class BusinessDay extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_business_days_store"))
    private Store store;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BusinessDayStatus status;

    @Column(nullable = false)
    private Instant openedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by", nullable = false, foreignKey = @ForeignKey(name = "fk_business_days_opened_by"))
    private User openedBy;

    @Column
    private Instant closingStartedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closing_started_by", foreignKey = @ForeignKey(name = "fk_business_days_closing_started_by"))
    private User closingStartedBy;

    @Column
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by", foreignKey = @ForeignKey(name = "fk_business_days_closed_by"))
    private User closedBy;

    @Column(length = 1000)
    private String reopenReason;

    @Column
    private Instant reopenedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reopened_by", foreignKey = @ForeignKey(name = "fk_business_days_reopened_by"))
    private User reopenedBy;

    @Column(length = 1000)
    private String forceCloseReason;

    protected BusinessDay() {
    }

    public BusinessDay(Store store, LocalDate businessDate, String timezone, User openedBy, Instant openedAt) {
        this.store = store;
        this.businessDate = businessDate;
        this.timezone = timezone;
        this.status = BusinessDayStatus.OPEN;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
        initializeIdAndTimestamps();
    }

    public void startClosing(User actor, Instant at) {
        this.status = BusinessDayStatus.CLOSING;
        this.closingStartedBy = actor;
        this.closingStartedAt = at;
    }

    public void close(User actor, Instant at, String forceCloseReason) {
        this.status = BusinessDayStatus.CLOSED;
        this.closedBy = actor;
        this.closedAt = at;
        this.forceCloseReason = forceCloseReason;
    }

    public void reopen(User actor, Instant at, String reason) {
        this.status = BusinessDayStatus.REOPENED;
        this.closingStartedBy = null;
        this.closingStartedAt = null;
        this.closedBy = null;
        this.closedAt = null;
        this.reopenedBy = actor;
        this.reopenedAt = at;
        this.reopenReason = reason;
    }

    public Store getStore() {
        return store;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getTimezone() {
        return timezone;
    }

    public BusinessDayStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public User getOpenedBy() {
        return openedBy;
    }

    public Instant getClosingStartedAt() {
        return closingStartedAt;
    }

    public User getClosingStartedBy() {
        return closingStartedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public User getClosedBy() {
        return closedBy;
    }

    public String getReopenReason() {
        return reopenReason;
    }

    public Instant getReopenedAt() {
        return reopenedAt;
    }

    public User getReopenedBy() {
        return reopenedBy;
    }

    public String getForceCloseReason() {
        return forceCloseReason;
    }
}
