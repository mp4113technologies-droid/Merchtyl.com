package com.merchtyl.inventory;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.Product;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "balance_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_inventory_transactions_balance"))
    private InventoryBalance balance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_inventory_transactions_store"))
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_inventory_transactions_product"))
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private InventoryTransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal quantityDelta;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal resultingQuantity;

    @Column(length = 80, updatable = false)
    private String referenceType;

    @Column(updatable = false)
    private UUID referenceId;

    @Column(length = 1000, updatable = false)
    private String reason;

    @Column(updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    protected InventoryTransaction() {
    }

    public InventoryTransaction(
            InventoryBalance balance,
            InventoryTransactionType transactionType,
            BigDecimal quantityDelta,
            BigDecimal resultingQuantity,
            String referenceType,
            UUID referenceId,
            String reason,
            UUID actorUserId,
            Instant occurredAt) {
        this.balance = balance;
        this.store = balance.getStore();
        this.product = balance.getProduct();
        this.transactionType = transactionType;
        this.quantityDelta = quantityDelta;
        this.resultingQuantity = resultingQuantity;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reason = reason;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
        initializeIdAndTimestamps();
    }

    public InventoryBalance getBalance() {
        return balance;
    }

    public Store getStore() {
        return store;
    }

    public Product getProduct() {
        return product;
    }

    public InventoryTransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public BigDecimal getResultingQuantity() {
        return resultingQuantity;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getReason() {
        return reason;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
