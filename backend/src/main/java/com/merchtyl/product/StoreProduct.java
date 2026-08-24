package com.merchtyl.product;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.store.Store;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "store_products", uniqueConstraints = @UniqueConstraint(name = "uq_store_products_tenant_store_product", columnNames = {"tenant_id", "store_id", "product_id"}))
public class StoreProduct extends BaseUuidEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, updatable = false)
    private Store store;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private boolean sellable;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal sellingPrice;
    @Column(precision = 19, scale = 4) private BigDecimal costPrice;
    @Column(precision = 19, scale = 4) private BigDecimal minimumSellingPrice;
    @Column(precision = 19, scale = 4) private BigDecimal lowStockThreshold;
    @Column(nullable = false) private boolean allowDiscount;
    @Column(nullable = false) private boolean allowPriceOverride;

    protected StoreProduct() {}
    public StoreProduct(UUID tenantId, Store store, Product product) {
        this.tenantId=tenantId; this.store=store; this.product=product; this.active=true; this.sellable=true;
        this.sellingPrice=product.getPrice(); this.costPrice=product.getCost(); this.allowDiscount=true; this.allowPriceOverride=true;
        initializeIdAndTimestamps();
    }
    public void update(StoreProductRequest request) {
        active=request.active(); sellable=request.sellable(); sellingPrice=request.sellingPrice(); costPrice=request.costPrice();
        minimumSellingPrice=request.minimumSellingPrice(); lowStockThreshold=request.lowStockThreshold();
        allowDiscount=request.allowDiscount(); allowPriceOverride=request.allowPriceOverride();
    }
    public UUID getTenantId(){return tenantId;} public Store getStore(){return store;} public Product getProduct(){return product;}
    public boolean isActive(){return active;} public boolean isSellable(){return sellable;} public BigDecimal getSellingPrice(){return sellingPrice;}
    public BigDecimal getCostPrice(){return costPrice;} public BigDecimal getMinimumSellingPrice(){return minimumSellingPrice;}
    public BigDecimal getLowStockThreshold(){return lowStockThreshold;} public boolean isAllowDiscount(){return allowDiscount;}
    public boolean isAllowPriceOverride(){return allowPriceOverride;}
}
