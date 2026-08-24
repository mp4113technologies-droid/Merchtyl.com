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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "tax_jurisdictions",
        uniqueConstraints = @UniqueConstraint(name = "uq_tax_jurisdictions_country_code", columnNames = {"country_id", "code"}))
public class TaxJurisdiction extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tax_jurisdictions_country"))
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrative_area_id", foreignKey = @ForeignKey(name = "fk_tax_jurisdictions_area"))
    private AdministrativeArea administrativeArea;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaxJurisdictionType type;

    @Column(nullable = false)
    private boolean active;

    protected TaxJurisdiction() {
    }

    public TaxJurisdiction(Country country, AdministrativeArea administrativeArea, String code, String name, TaxJurisdictionType type, boolean active) {
        update(country, administrativeArea, code, name, type, active);
        initializeIdAndTimestamps();
    }

    public void update(Country country, AdministrativeArea administrativeArea, String code, String name, TaxJurisdictionType type, boolean active) {
        this.country = country;
        this.administrativeArea = administrativeArea;
        this.code = code;
        this.name = name;
        this.type = type;
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Country getCountry() {
        return country;
    }

    public AdministrativeArea getAdministrativeArea() {
        return administrativeArea;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public TaxJurisdictionType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }
}
