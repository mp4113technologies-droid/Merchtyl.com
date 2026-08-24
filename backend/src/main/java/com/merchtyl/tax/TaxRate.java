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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tax_rates")
public class TaxRate extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_component_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tax_rates_component"))
    private TaxComponent taxComponent;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal percentageRate;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean includedInPrice;

    @Column(nullable = false)
    private boolean compoundOnPreviousTax;

    @Column(nullable = false)
    private int calculationOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaxRateStatus status;

    @Column(length = 180)
    private String source;

    @Column(length = 500)
    private String sourceReference;

    @Column(length = 180)
    private String verifiedBy;

    private Instant verifiedAt;

    protected TaxRate() {
    }

    public TaxRate(TaxRateValues values) {
        update(values);
        initializeIdAndTimestamps();
    }

    public void update(TaxRateValues values) {
        this.taxComponent = values.taxComponent();
        this.percentageRate = values.percentageRate();
        this.effectiveFrom = values.effectiveFrom();
        this.effectiveTo = values.effectiveTo();
        this.includedInPrice = values.includedInPrice();
        this.compoundOnPreviousTax = values.compoundOnPreviousTax();
        this.calculationOrder = values.calculationOrder();
        this.status = values.status();
        this.source = values.source();
        this.sourceReference = values.sourceReference();
        this.verifiedBy = values.verifiedBy();
        this.verifiedAt = values.verifiedAt();
    }

    public void setStatus(TaxRateStatus status) {
        this.status = status;
    }

    public TaxComponent getTaxComponent() {
        return taxComponent;
    }

    public BigDecimal getPercentageRate() {
        return percentageRate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public boolean isIncludedInPrice() {
        return includedInPrice;
    }

    public boolean isCompoundOnPreviousTax() {
        return compoundOnPreviousTax;
    }

    public int getCalculationOrder() {
        return calculationOrder;
    }

    public TaxRateStatus getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
