package com.merchtyl.returns;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity(name = "MerchtylReturn")
@Table(name = "returns")
public class Return extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_sale_id", nullable = false, foreignKey = @ForeignKey(name = "fk_returns_original_sale"))
    private Sale originalSale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_returns_store"))
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, foreignKey = @ForeignKey(name = "fk_returns_register"))
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_returns_register_session"))
    private RegisterSession registerSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_returns_created_by"))
    private User createdBy;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal totalQuantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "returnRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<ReturnItem> items = new ArrayList<>();

    protected Return() {
    }

    Return(Sale originalSale, User createdBy, Instant occurredAt, String reason) {
        this.originalSale = originalSale;
        this.store = originalSale.getStore();
        this.register = originalSale.getRegister();
        this.registerSession = originalSale.getRegisterSession();
        this.createdBy = createdBy;
        this.businessDate = originalSale.getBusinessDate();
        this.occurredAt = occurredAt;
        this.currencyCode = originalSale.getCurrencyCode();
        this.reason = reason;
        this.totalQuantity = BigDecimal.ZERO.setScale(4);
        this.subtotalAmount = BigDecimal.ZERO.setScale(2);
        this.taxAmount = BigDecimal.ZERO.setScale(2);
        this.totalAmount = BigDecimal.ZERO.setScale(2);
        initializeIdAndTimestamps();
    }

    void addItem(ReturnItem item) {
        item.assignLineNumber(items.size() + 1);
        items.add(item);
        recalculateTotals();
    }

    private void recalculateTotals() {
        this.totalQuantity = items.stream()
                .map(ReturnItem::getQuantity)
                .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
        this.subtotalAmount = items.stream()
                .map(ReturnItem::getReturnSubtotalAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        this.taxAmount = items.stream()
                .map(ReturnItem::getReturnTaxAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        this.totalAmount = items.stream()
                .map(ReturnItem::getReturnTotalAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
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

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
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

    public List<ReturnItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
