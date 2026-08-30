package com.merchtyl.foodmenu;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.store.Store;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "food_menu_categories", uniqueConstraints = @UniqueConstraint(name = "uq_food_menu_categories_store_name", columnNames = {"store_id", "name"}))
public class FoodMenuCategory extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", nullable = false, updatable = false) private Store store;
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(nullable = false) private boolean active;
    @Column(name = "image_url", length = 1000) private String imageUrl;
    protected FoodMenuCategory() {}
    FoodMenuCategory(Store store, String name, int displayOrder, boolean active, String imageUrl) { this.store=store; this.tenantId=store.getTenantId(); update(name, displayOrder, active, imageUrl); }
    void update(String name, int displayOrder, boolean active, String imageUrl) { this.name=name.trim(); this.displayOrder=displayOrder; this.active=active; this.imageUrl=blankToNull(imageUrl); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public Store getStore(){return store;} public UUID getTenantId(){return tenantId;} public String getName(){return name;} public int getDisplayOrder(){return displayOrder;} public boolean isActive(){return active;} public String getImageUrl(){return imageUrl;}
}
