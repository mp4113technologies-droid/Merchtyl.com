package com.merchtyl.foodmenu;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="food_menu_item_variants")
public class FoodMenuItemVariant extends BaseUuidEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="menu_item_id", nullable=false) private FoodMenuItem menuItem;
    @Column(nullable=false,length=120) private String name;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal price;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(nullable=false) private boolean available;
    protected FoodMenuItemVariant() {}
    FoodMenuItemVariant(FoodMenuItem item,String name,BigDecimal price,int order,boolean available){menuItem=item;update(name,price,order,available);}
    void update(String name,BigDecimal price,int order,boolean available){this.name=name.trim();this.price=price;displayOrder=order;this.available=available;}
    public String getName(){return name;} public BigDecimal getPrice(){return price;} public int getDisplayOrder(){return displayOrder;} public boolean isAvailable(){return available;}
}
