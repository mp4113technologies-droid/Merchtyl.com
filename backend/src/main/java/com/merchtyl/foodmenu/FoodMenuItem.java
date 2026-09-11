package com.merchtyl.foodmenu;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.product.Product;
import com.merchtyl.store.Store;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.*;

@Entity
@Table(name = "food_menu_items", uniqueConstraints = @UniqueConstraint(name = "uq_food_menu_items_store_product", columnNames = {"store_id", "product_id"}))
public class FoodMenuItem extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="store_id", nullable=false, updatable=false) private Store store;
    @Column(name="tenant_id", nullable=false, updatable=false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="category_id", nullable=false) private FoodMenuCategory category;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="product_id", nullable=false, updatable=false) private Product product;
    @Column(name="display_name", nullable=false, length=180) private String displayName;
    @Column(length=1000) private String description;
    @Column(nullable=false, precision=19, scale=4) private BigDecimal price;
    @Column(name="display_order", nullable=false) private int displayOrder;
    @Column(nullable=false) private boolean available;
    @Column(name="image_url", length=1000) private String imageUrl;
    @Column(name="linked_product", nullable=false) private boolean linkedProduct;
    @OneToMany(mappedBy="menuItem",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("displayOrder, name") private Set<FoodMenuItemVariant> variants=new LinkedHashSet<>();
    @OneToMany(mappedBy="menuItem",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("displayOrder, name") private Set<FoodMenuModifierGroup> modifierGroups=new LinkedHashSet<>();
    @OneToMany(mappedBy="menuItem",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("displayOrder, name") private Set<FoodMenuItemComponent> components=new LinkedHashSet<>();
    protected FoodMenuItem() {}
    FoodMenuItem(Store store, FoodMenuCategory category, Product product, boolean linkedProduct, String displayName, String description, BigDecimal price, int displayOrder, boolean available, String imageUrl) { this.store=store; this.tenantId=store.getTenantId(); this.product=product; this.linkedProduct=linkedProduct; update(category, displayName, description, price, displayOrder, available, imageUrl); }
    void update(FoodMenuCategory category, String displayName, String description, BigDecimal price, int displayOrder, boolean available, String imageUrl) { this.category=category; this.displayName=displayName.trim(); this.description=description == null || description.isBlank() ? null : description.trim(); this.price=price; this.displayOrder=displayOrder; this.available=available; this.imageUrl=imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim(); }
    void clearSelections(){variants.clear();modifierGroups.clear();components.clear();}
    void addSelections(List<FoodMenuDtos.VariantRequest> variantValues,List<FoodMenuDtos.ModifierGroupRequest> groupValues,List<FoodMenuDtos.ComponentRequest> componentValues){variantValues.forEach(v->variants.add(new FoodMenuItemVariant(this,v.name(),v.price(),v.displayOrder(),v.available())));groupValues.forEach(g->{var value=new FoodMenuModifierGroup(this,g.name(),g.minimumSelections(),g.maximumSelections(),g.displayOrder());value.replaceOptions(g.options());modifierGroups.add(value);});componentValues.forEach(c->components.add(new FoodMenuItemComponent(this,c.name(),c.includedByDefault(),c.removable(),c.allowExtra(),c.extraPrice(),c.displayOrder(),c.active())));}
    void replaceSelections(List<FoodMenuDtos.VariantRequest> variantValues,List<FoodMenuDtos.ModifierGroupRequest> groupValues,List<FoodMenuDtos.ComponentRequest> componentValues){clearSelections();addSelections(variantValues,groupValues,componentValues);}
    public Store getStore(){return store;} public UUID getTenantId(){return tenantId;} public FoodMenuCategory getCategory(){return category;} public Product getProduct(){return product;} public boolean isLinkedProduct(){return linkedProduct;} public String getDisplayName(){return displayName;} public String getDescription(){return description;} public BigDecimal getPrice(){return price;} public int getDisplayOrder(){return displayOrder;} public boolean isAvailable(){return available;} public String getImageUrl(){return imageUrl;} public Set<FoodMenuItemVariant> getVariants(){return variants;} public Set<FoodMenuModifierGroup> getModifierGroups(){return modifierGroups;} public Set<FoodMenuItemComponent> getComponents(){return components;}
}
