package com.merchtyl.refunds;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.sales.Payment;
import com.merchtyl.sales.PaymentMethod;
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

@Entity
@Table(name = "refund_payments")
public class RefundPayment extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refund_payments_refund"))
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_payment_id", updatable = false, foreignKey = @ForeignKey(name = "fk_refund_payments_original_payment"))
    private Payment originalPayment;

    @Column(nullable = false, updatable = false)
    private int lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private PaymentMethod method;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Column(name = "manual_reference", length = 120, updatable = false)
    private String reference;

    @Column(length = 500, updatable = false)
    private String notes;

    protected RefundPayment() {
    }

    RefundPayment(Refund refund, Payment originalPayment, PaymentMethod method, BigDecimal amount, String currencyCode, String reference, String notes) {
        this.refund = refund;
        this.originalPayment = originalPayment;
        this.method = method;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.reference = reference;
        this.notes = notes;
        initializeIdAndTimestamps();
    }

    void assignLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Refund getRefund() {
        return refund;
    }

    public Payment getOriginalPayment() {
        return originalPayment;
    }

    public int getLineNumber() {
        return lineNumber;
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

    public String getReference() {
        return reference;
    }

    public String getNotes() {
        return notes;
    }
}
