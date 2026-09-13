package com.merchtyl.foodmenu;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "food_menu_item_modifier_group_assignments")
public class FoodMenuItemModifierGroupAssignment extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private FoodMenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modifier_group_id", nullable = false)
    private FoodMenuModifierGroup group;

    @Column(name = "minimum_selections", nullable = false) private int minimumSelections;
    @Column(name = "maximum_selections", nullable = false) private int maximumSelections;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(nullable = false) private boolean active;

    protected FoodMenuItemModifierGroupAssignment() {}

    FoodMenuItemModifierGroupAssignment(FoodMenuItem menuItem, FoodMenuModifierGroup group, int minimumSelections,
                                        int maximumSelections, int displayOrder, boolean active) {
        this.menuItem = menuItem;
        this.group = group;
        this.minimumSelections = minimumSelections;
        this.maximumSelections = maximumSelections;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public FoodMenuModifierGroup getGroup() { return group; }
    public FoodMenuItem getMenuItem() { return menuItem; }
    public int getMinimumSelections() { return minimumSelections; }
    public int getMaximumSelections() { return maximumSelections; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
