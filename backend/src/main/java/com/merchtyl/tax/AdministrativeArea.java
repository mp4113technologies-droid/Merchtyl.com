package com.merchtyl.tax;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.reference.TaxRegion;
import com.merchtyl.reference.TimezoneReference;
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
        name = "administrative_areas",
        uniqueConstraints = @UniqueConstraint(name = "uq_administrative_areas_country_code", columnNames = {"country_id", "code"}))
public class AdministrativeArea extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false, foreignKey = @ForeignKey(name = "fk_administrative_areas_country"))
    private Country country;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdministrativeAreaType type;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_timezone_id", foreignKey = @ForeignKey(name = "fk_administrative_areas_default_timezone"))
    private TimezoneReference defaultTimezone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_tax_region_id", foreignKey = @ForeignKey(name = "fk_administrative_areas_default_tax_region"))
    private TaxRegion defaultTaxRegion;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected AdministrativeArea() {
    }

    public AdministrativeArea(Country country, String code, String name, AdministrativeAreaType type, boolean active) {
        this.country = country;
        this.code = code;
        this.name = name;
        this.type = type;
        this.active = active;
        initializeIdAndTimestamps();
    }

    public void update(Country country, String code, String name, AdministrativeAreaType type, boolean active) {
        this.country = country;
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

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AdministrativeAreaType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public TimezoneReference getDefaultTimezone() {
        return defaultTimezone;
    }

    public TaxRegion getDefaultTaxRegion() {
        return defaultTaxRegion;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
