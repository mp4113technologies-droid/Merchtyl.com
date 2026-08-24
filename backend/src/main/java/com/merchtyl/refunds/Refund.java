package com.merchtyl.refunds;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.returns.Return;
import com.merchtyl.sales.Sale;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        name = "refunds",
        uniqueConstraints = @UniqueConstraint(name = "uq_refunds_return", columnNames = "return_id"))
public class Refund extends BaseUuidEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refunds_return"))
    private Return returnRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_sale_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refunds_original_sale"))
    private Sale originalSale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refunds_store"))
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refunds_register"))
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_session_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refunds_register_session"))
    private RegisterSession registerSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refunds_created_by"))
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", updatable = false, foreignKey = @ForeignKey(name = "fk_refunds_approved_by"))
    private User approvedBy;

    @Column(updatable = false)
    private Instant approvedAt;

    @Column(length = 1000, updatable = false)
    private String approvalNotes;

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("lineNumber ASC")
    @BatchSize(size = 100)
    private List<RefundPayment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("lineNumber ASC")
    @BatchSize(size = 100)
    private List<RefundItemTax> itemTaxes = new ArrayList<>();

    protected Refund() {
    }

    Refund(Return returnRecord, User createdBy, Instant occurredAt, String reason, User approvedBy, Instant approvedAt, String approvalNotes) {
        this.returnRecord = returnRecord;
        this.originalSale = returnRecord.getOriginalSale();
        this.store = returnRecord.getStore();
        this.register = returnRecord.getRegister();
        this.registerSession = returnRecord.getRegisterSession();
        this.createdBy = createdBy;
        this.businessDate = returnRecord.getBusinessDate();
        this.occurredAt = occurredAt;
        this.currencyCode = returnRecord.getCurrencyCode();
        this.reason = reason;
        this.subtotalAmount = returnRecord.getSubtotalAmount();
        this.taxAmount = returnRecord.getTaxAmount();
        this.totalAmount = returnRecord.getTotalAmount();
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.approvalNotes = approvalNotes;
        initializeIdAndTimestamps();
    }

    void addPayment(RefundPayment payment) {
        payment.assignLineNumber(payments.size() + 1);
        payments.add(payment);
    }

    void addItemTax(RefundItemTax itemTax) {
        itemTax.assignLineNumber(itemTaxes.size() + 1);
        itemTaxes.add(itemTax);
    }

    public Return getReturnRecord() {
        return returnRecord;
    }

    public Sale getOriginalSale() {
        return originalSale;
    }

    public Store getStore() {
        return store;
    }

    public Register getRegister() {
        return register;
    }

    public RegisterSession getRegisterSession() {
        return registerSession;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getApprovalNotes() {
        return approvalNotes;
    }

    public List<RefundPayment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    public List<RefundItemTax> getItemTaxes() {
        return Collections.unmodifiableList(itemTaxes);
    }
}
