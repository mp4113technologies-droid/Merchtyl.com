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
@Table(name = "tax_rule_conditions")
public class TaxRuleCondition extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_rule_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tax_rule_conditions_rule"))
    private TaxRule taxRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private TaxRuleConditionType conditionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaxRuleConditionOperator operator;

    @Column(length = 180)
    private String value;

    @Column(length = 180)
    private String secondValue;

    protected TaxRuleCondition() {
    }

    public TaxRuleCondition(TaxRule taxRule, TaxRuleConditionValues values) {
        this.taxRule = taxRule;
        this.conditionType = values.conditionType();
        this.operator = values.operator();
        this.value = values.value();
        this.secondValue = values.secondValue();
        initializeIdAndTimestamps();
    }

    public TaxRuleConditionType getConditionType() {
        return conditionType;
    }

    public TaxRuleConditionOperator getOperator() {
        return operator;
    }

    public String getValue() {
        return value;
    }

    public String getSecondValue() {
        return secondValue;
    }
}
