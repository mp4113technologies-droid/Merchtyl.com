package com.merchtyl.foodmenu;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="food_menu_modifier_options")
public class FoodMenuModifierOption extends BaseUuidEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="modifier_group_id",nullable=false) private FoodMenuModifierGroup group;
    @Column(nullable=false,length=120) private String name;
    @Column(name="price_adjustment",nullable=false,precision=19,scale=4) private BigDecimal priceAdjustment;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(nullable=false) private boolean available;
    protected FoodMenuModifierOption() {}
    FoodMenuModifierOption(FoodMenuModifierGroup group,String name,BigDecimal price,int order,boolean available){this.group=group;this.name=name.trim();priceAdjustment=price;displayOrder=order;this.available=available;}
    public FoodMenuModifierGroup getGroup(){return group;} public String getName(){return name;} public BigDecimal getPriceAdjustment(){return priceAdjustment;} public int getDisplayOrder(){return displayOrder;} public boolean isAvailable(){return available;}
}
