package com.merchtyl.tax;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tax_rules", uniqueConstraints = @UniqueConstraint(name = "uq_tax_rules_code", columnNames = "code"))
public class TaxRule extends BaseUuidEntity {
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "taxRule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("conditionType ASC, id ASC")
    private final List<TaxRuleCondition> conditions = new ArrayList<>();

    @OneToMany(mappedBy = "taxRule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("actionType ASC, id ASC")
    private final List<TaxRuleAction> actions = new ArrayList<>();

    protected TaxRule() {
    }

    public TaxRule(TaxRuleValues values) {
        update(values);
        initializeIdAndTimestamps();
    }

    public void update(TaxRuleValues values) {
        this.code = values.code();
        this.name = values.name();
        this.description = values.description();
        this.priority = values.priority();
        this.effectiveFrom = values.effectiveFrom();
        this.effectiveTo = values.effectiveTo();
        this.active = values.active();
        this.conditions.clear();
        values.conditions().forEach(conditionValues -> this.conditions.add(new TaxRuleCondition(this, conditionValues)));
        this.actions.clear();
        values.actions().forEach(actionValues -> this.actions.add(new TaxRuleAction(this, actionValues)));
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public boolean isActive() {
        return active;
    }

    public List<TaxRuleCondition> getConditions() {
        return List.copyOf(conditions);
    }

    public List<TaxRuleAction> getActions() {
        return List.copyOf(actions);
    }
}
