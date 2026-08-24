package com.merchtyl.lottery;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.tax.TaxJurisdiction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "lottery_operators",
        uniqueConstraints = @UniqueConstraint(name = "uq_lottery_operators_code", columnNames = "code"))
public class LotteryOperator extends BaseUuidEntity {
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jurisdiction_id", nullable = false)
    private TaxJurisdiction jurisdiction;

    @Column(length = 1000)
    private String supportContact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SettlementFrequency settlementFrequency;

    @Column(nullable = false)
    private boolean active;

    protected LotteryOperator() {
    }

    LotteryOperator(LotteryOperatorValues values) {
        update(values);
        initializeIdAndTimestamps();
    }

    void update(LotteryOperatorValues values) {
        this.code = values.code();
        this.name = values.name();
        this.jurisdiction = values.jurisdiction();
        this.supportContact = values.supportContact();
        this.settlementFrequency = values.settlementFrequency();
        this.active = values.active();
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public TaxJurisdiction getJurisdiction() {
        return jurisdiction;
    }

    public String getSupportContact() {
        return supportContact;
    }

    public SettlementFrequency getSettlementFrequency() {
        return settlementFrequency;
    }

    public boolean isActive() {
        return active;
    }
}
