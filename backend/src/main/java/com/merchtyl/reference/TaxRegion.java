package com.merchtyl.reference;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.tax.AdministrativeArea;
import com.merchtyl.tax.Country;
import com.merchtyl.tax.TaxJurisdiction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tax_regions")
public class TaxRegion extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tax_regions_country"))
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrative_division_id", foreignKey = @ForeignKey(name = "fk_tax_regions_division"))
    private AdministrativeArea administrativeDivision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_jurisdiction_id", foreignKey = @ForeignKey(name = "fk_tax_regions_jurisdiction"))
    private TaxJurisdiction taxJurisdiction;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "default_for_division", nullable = false)
    private boolean defaultForDivision;

    protected TaxRegion() {
    }

    public Country getCountry() {
        return country;
    }

    public AdministrativeArea getAdministrativeDivision() {
        return administrativeDivision;
    }

    public TaxJurisdiction getTaxJurisdiction() {
        return taxJurisdiction;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDefaultForDivision() {
        return defaultForDivision;
    }
}
