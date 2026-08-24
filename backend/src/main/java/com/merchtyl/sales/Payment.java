package com.merchtyl.sales;

import com.merchtyl.platform.persistence.BaseUuidEntity;
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
@Table(name = "payments")
public class Payment extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_payments_sale"))
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private PaymentMethod method;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(updatable = false, precision = 12, scale = 2)
    private BigDecimal cashTendered;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal changeDue;

    @Column(name = "manual_reference", updatable = false, length = 120)
    private String reference;

    @Column(updatable = false, length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_payments_created_by"))
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private Instant completedAt;

    protected Payment() {
    }

    Payment(
            Sale sale,
            PaymentMethod method,
            BigDecimal amount,
            String currencyCode,
            BigDecimal cashTendered,
            BigDecimal changeDue,
            String reference,
            String notes,
            User createdBy,
            Instant completedAt) {
        this.sale = sale;
        this.method = method;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.cashTendered = cashTendered;
        this.changeDue = changeDue;
        this.reference = reference;
        this.notes = notes;
        this.createdBy = createdBy;
        this.completedAt = completedAt;
        initializeIdAndTimestamps();
    }

    public Sale getSale() {
        return sale;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getCashTendered() {
        return cashTendered;
    }

    public BigDecimal getChangeDue() {
        return changeDue;
    }

    public String getReference() {
        return reference;
    }

    public String getNotes() {
        return notes;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
