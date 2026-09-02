package com.merchtyl.sales;

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
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales")
public class Sale extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sales_store"))
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sales_register"))
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sales_register_session"))
    private RegisterSession registerSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_sales_created_by"))
    private User createdBy;

    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SaleStatus status;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(length = 40)
    private String saleChannel;

    @Column(name = "food_order_token", length = 16)
    private String foodOrderToken;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private boolean pricesIncludeTax;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedTaxAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column
    private Instant heldAt;

    @Column
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by", foreignKey = @ForeignKey(name = "fk_sales_completed_by"))
    private User completedBy;

    @Column
    private Instant completedAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    @BatchSize(size = 100)
    private List<SaleItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("completedAt ASC")
    @BatchSize(size = 100)
    private List<Payment> payments = new ArrayList<>();

    protected Sale() {
    }

    Sale(
            Store store,
            Register register,
            RegisterSession registerSession,
            User createdBy,
            UUID customerId,
            LocalDate businessDate,
            String saleChannel,
            String currencyCode,
            boolean pricesIncludeTax) {
        this.store = store;
        this.register = register;
        this.registerSession = registerSession;
        this.createdBy = createdBy;
        this.customerId = customerId;
        this.status = SaleStatus.DRAFT;
        this.businessDate = businessDate;
        this.saleChannel = saleChannel;
        this.currencyCode = currencyCode;
        this.pricesIncludeTax = pricesIncludeTax;
        this.subtotalAmount = moneyZero();
        this.discountAmount = moneyZero();
        this.estimatedTaxAmount = moneyZero();
        this.totalAmount = moneyZero();
        initializeIdAndTimestamps();
    }

    void addItem(SaleItem item) {
        item.assignLineNumber(items.size() + 1);
        items.add(item);
    }

    void addPayment(Payment payment) {
        payments.add(payment);
    }

    void removeItem(SaleItem item) {
        items.remove(item);
        resequenceItems();
    }

    void setTotals(BigDecimal subtotalAmount, BigDecimal discountAmount, BigDecimal estimatedTaxAmount, BigDecimal totalAmount) {
        this.subtotalAmount = subtotalAmount;
        this.discountAmount = discountAmount;
        this.estimatedTaxAmount = estimatedTaxAmount;
        this.totalAmount = totalAmount;
    }

    void hold(Instant heldAt) {
        this.status = SaleStatus.HELD;
        this.heldAt = heldAt;
    }

    void resume() {
        this.status = SaleStatus.DRAFT;
        this.heldAt = null;
    }

    void cancel(Instant cancelledAt) {
        this.status = SaleStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    void complete(User completedBy, Instant completedAt) {
        this.status = SaleStatus.COMPLETED;
        this.completedBy = completedBy;
        this.completedAt = completedAt;
    }

    void assignFoodOrderToken(String foodOrderToken) {
        if (this.foodOrderToken == null) {
            this.foodOrderToken = foodOrderToken;
        }
    }

    public void markRefundStatus(boolean fullyRefunded) {
        this.status = fullyRefunded ? SaleStatus.REFUNDED : SaleStatus.PARTIALLY_REFUNDED;
    }

    private void resequenceItems() {
        for (int index = 0; index < items.size(); index++) {
            items.get(index).assignLineNumber(index + 1);
        }
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

    public UUID getCustomerId() {
        return customerId;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getSaleChannel() {
        return saleChannel;
    }

    public String getFoodOrderToken() {
        return foodOrderToken;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public boolean isPricesIncludeTax() {
        return pricesIncludeTax;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getEstimatedTaxAmount() {
        return estimatedTaxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getHeldAt() {
        return heldAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public User getCompletedBy() {
        return completedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<SaleItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<Payment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    private static BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
