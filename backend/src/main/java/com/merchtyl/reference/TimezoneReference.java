package com.merchtyl.reference;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.tax.Country;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "timezone_reference")
public class TimezoneReference extends BaseUuidEntity {
    @Column(name = "iana_name", nullable = false, unique = true, length = 64)
    private String ianaName;

    @Column(name = "display_name", nullable = false, length = 180)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", foreignKey = @ForeignKey(name = "fk_timezone_reference_country"))
    private Country country;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected TimezoneReference() {
    }

    public String getIanaName() {
        return ianaName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Country getCountry() {
        return country;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
