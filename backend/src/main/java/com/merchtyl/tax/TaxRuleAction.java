package com.merchtyl.tax;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tax_rule_actions")
public class TaxRuleAction extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_rule_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tax_rule_actions_rule"))
    private TaxRule taxRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private TaxRuleActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_group_id", foreignKey = @ForeignKey(name = "fk_tax_rule_actions_group"))
    private TaxGroup taxGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_component_id", foreignKey = @ForeignKey(name = "fk_tax_rule_actions_component"))
    private TaxComponent taxComponent;

    @Column(length = 180)
    private String value;

    protected TaxRuleAction() {
    }

    public TaxRuleAction(TaxRule taxRule, TaxRuleActionValues values) {
        this.taxRule = taxRule;
        this.actionType = values.actionType();
        this.taxGroup = values.taxGroup();
        this.taxComponent = values.taxComponent();
        this.value = values.value();
        initializeIdAndTimestamps();
    }

    public TaxRuleActionType getActionType() {
        return actionType;
    }

    public TaxGroup getTaxGroup() {
        return taxGroup;
    }

    public TaxComponent getTaxComponent() {
        return taxComponent;
    }

    public String getValue() {
        return value;
    }
}
