package com.merchtyl.posquickkey;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.ProductVariant;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "pos_quick_keys")
public class PosQuickKey extends BaseUuidEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pos_quick_keys_variant"))
    private ProductVariant productVariant;
    @Column(name = "display_label", length = 80)
    private String displayLabel;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(nullable = false)
    private boolean active;

    protected PosQuickKey() {}

    PosQuickKey(UUID tenantId, ProductVariant variant, String displayLabel, int displayOrder) {
        this.tenantId = tenantId;
        this.productVariant = variant;
        update(displayLabel, displayOrder, true);
        initializeIdAndTimestamps();
    }

    void update(String displayLabel, int displayOrder, boolean active) {
        this.displayLabel = displayLabel == null || displayLabel.isBlank() ? null : displayLabel.trim();
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public UUID getTenantId() { return tenantId; }
    public ProductVariant getProductVariant() { return productVariant; }
    public String getDisplayLabel() { return displayLabel; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
