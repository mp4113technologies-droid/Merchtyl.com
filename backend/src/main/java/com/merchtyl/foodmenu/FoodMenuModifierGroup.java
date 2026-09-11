package com.merchtyl.foodmenu;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.*;
import java.util.*;

@Entity @Table(name="food_menu_modifier_groups")
public class FoodMenuModifierGroup extends BaseUuidEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="menu_item_id",nullable=false) private FoodMenuItem menuItem;
    @Column(nullable=false,length=120) private String name;
    @Column(name="minimum_selections",nullable=false) private int minimumSelections;
    @Column(name="maximum_selections",nullable=false) private int maximumSelections;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @OneToMany(mappedBy="group",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("displayOrder, name") private List<FoodMenuModifierOption> options=new ArrayList<>();
    protected FoodMenuModifierGroup() {}
    FoodMenuModifierGroup(FoodMenuItem item,String name,int min,int max,int order){menuItem=item;this.name=name.trim();minimumSelections=min;maximumSelections=max;displayOrder=order;}
    void replaceOptions(List<FoodMenuDtos.ModifierOptionRequest> values){options.clear();values.forEach(v->options.add(new FoodMenuModifierOption(this,v.name(),v.priceAdjustment(),v.displayOrder(),v.available())));}
    public String getName(){return name;} public int getMinimumSelections(){return minimumSelections;} public int getMaximumSelections(){return maximumSelections;} public int getDisplayOrder(){return displayOrder;} public List<FoodMenuModifierOption> getOptions(){return options;}
}
