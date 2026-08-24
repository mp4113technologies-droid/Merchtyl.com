package com.merchtyl.lottery;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.store.Store;
import com.merchtyl.tax.TaxJurisdiction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "lottery_commission_rules")
public class LotteryCommissionRule extends BaseUuidEntity {
    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_commission_rules_operator"))
    private LotteryOperator operator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jurisdiction_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_commission_rules_jurisdiction"))
    private TaxJurisdiction jurisdiction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_commission_rules_store"))
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotteryCommissionRuleType ruleType;

    @Column(precision = 9, scale = 4)
    private BigDecimal commissionRatePercent;

    @Column(precision = 19, scale = 2)
    private BigDecimal fixedAmount;

    @Column(length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private LotteryCommissionPeriod fixedPeriod;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LotteryCommissionRuleStatus status;

    @Column(length = 500)
    private String notes;

    protected LotteryCommissionRule() {
    }

    LotteryCommissionRule(LotteryCommissionRuleValues values) {
        update(values);
        initializeIdAndTimestamps();
    }

    void update(LotteryCommissionRuleValues values) {
        this.name = values.name();
        this.operator = values.operator();
        this.jurisdiction = values.jurisdiction();
        this.store = values.store();
        this.ruleType = values.ruleType();
        this.commissionRatePercent = values.commissionRatePercent();
        this.fixedAmount = values.fixedAmount();
        this.currencyCode = values.currencyCode();
        this.fixedPeriod = values.fixedPeriod();
        this.effectiveFrom = values.effectiveFrom();
        this.effectiveTo = values.effectiveTo();
        this.status = values.status();
        this.notes = values.notes();
    }

    public String getName() {
        return name;
    }

    public LotteryOperator getOperator() {
        return operator;
    }

    public TaxJurisdiction getJurisdiction() {
        return jurisdiction;
    }

    public Store getStore() {
        return store;
    }

    public LotteryCommissionRuleType getRuleType() {
        return ruleType;
    }

    public BigDecimal getCommissionRatePercent() {
        return commissionRatePercent;
    }

    public BigDecimal getFixedAmount() {
        return fixedAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LotteryCommissionPeriod getFixedPeriod() {
        return fixedPeriod;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public LotteryCommissionRuleStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
