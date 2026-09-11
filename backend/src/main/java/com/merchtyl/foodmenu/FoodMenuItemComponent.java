package com.merchtyl.foodmenu;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="food_menu_item_components")
public class FoodMenuItemComponent extends BaseUuidEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="menu_item_id",nullable=false) private FoodMenuItem menuItem;
    @Column(nullable=false,length=120) private String name;
    @Column(name="included_by_default",nullable=false) private boolean includedByDefault;
    @Column(nullable=false) private boolean removable;
    @Column(name="allow_extra",nullable=false) private boolean allowExtra;
    @Column(name="extra_price",nullable=false,precision=19,scale=4) private BigDecimal extraPrice;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(nullable=false) private boolean active;
    protected FoodMenuItemComponent() {}
    FoodMenuItemComponent(FoodMenuItem item,String name,boolean included,boolean removable,boolean allowExtra,BigDecimal extraPrice,int order,boolean active){this.menuItem=item;this.name=name.trim();this.includedByDefault=included;this.removable=removable;this.allowExtra=allowExtra;this.extraPrice=allowExtra&&extraPrice!=null?extraPrice:BigDecimal.ZERO;this.displayOrder=order;this.active=active;}
    public String getName(){return name;} public boolean isIncludedByDefault(){return includedByDefault;} public boolean isRemovable(){return removable;} public boolean isAllowExtra(){return allowExtra;} public BigDecimal getExtraPrice(){return extraPrice;} public int getDisplayOrder(){return displayOrder;} public boolean isActive(){return active;}
}
