package com.merchtyl.inventory;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductVariant;
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
import java.time.Instant;

@Entity
@Table(
        name = "inventory_balances",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_inventory_balances_store_product_variant",
                columnNames = {"store_id", "product_id", "variant_id"}))
public class InventoryBalance extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inventory_balances_store"))
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inventory_balances_product"))
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="variant_id",foreignKey=@ForeignKey(name="fk_inventory_balances_variant"))
    private ProductVariant variant;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityOnHand;

    @Column(nullable = false)
    private Instant lastTransactionAt;

    protected InventoryBalance() {
    }

    public InventoryBalance(Store store, Product product, BigDecimal quantityOnHand, Instant lastTransactionAt) {
        this(store,product,null,quantityOnHand,lastTransactionAt);
    }

    public InventoryBalance(Store store, Product product, ProductVariant variant, BigDecimal quantityOnHand, Instant lastTransactionAt) {
        this.store = store;
        this.product = product;
        this.variant = variant;
        this.quantityOnHand = quantityOnHand;
        this.lastTransactionAt = lastTransactionAt;
        initializeIdAndTimestamps();
    }

    public void apply(BigDecimal quantityDelta, Instant occurredAt) {
        quantityOnHand = quantityOnHand.add(quantityDelta);
        lastTransactionAt = occurredAt;
    }

    public Store getStore() {
        return store;
    }

    public Product getProduct() {
        return product;
    }
    public ProductVariant getVariant(){return variant;}

    public BigDecimal getQuantityOnHand() {
        return quantityOnHand;
    }

    public Instant getLastTransactionAt() {
        return lastTransactionAt;
    }
}
