package com.merchtyl.foodmenu;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.store.Store;
import jakarta.persistence.*;
import java.util.*;

@Entity @Table(name="food_menu_modifier_groups")
public class FoodMenuModifierGroup extends BaseUuidEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="store_id",nullable=false) private Store store;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(nullable=false,length=120) private String name;
    @Column(nullable=false) private boolean active;
    @OneToMany(mappedBy="group",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("displayOrder, name") private List<FoodMenuModifierOption> options=new ArrayList<>();
    protected FoodMenuModifierGroup() {}
    FoodMenuModifierGroup(Store store,String name,boolean active){this.store=store;tenantId=store.getTenantId();update(name,active);}
    void update(String name,boolean active){this.name=name.trim();this.active=active;}
    void clearOptions(){options.clear();}
    void addOptions(List<FoodMenuDtos.ModifierOptionRequest> values){values.forEach(v->options.add(new FoodMenuModifierOption(this,v.name(),v.priceAdjustment(),v.displayOrder(),v.available())));}
    void replaceOptions(List<FoodMenuDtos.ModifierOptionRequest> values){clearOptions();addOptions(values);}
    public Store getStore(){return store;} public UUID getTenantId(){return tenantId;} public String getName(){return name;} public boolean isActive(){return active;} public List<FoodMenuModifierOption> getOptions(){return options;}
}
