package com.merchtyl.reference;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.tax.AdministrativeArea;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "administrative_division_timezone",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_administrative_division_timezone",
                columnNames = {"administrative_division_id", "timezone_id"}))
public class AdministrativeDivisionTimezone extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "administrative_division_id", nullable = false, foreignKey = @ForeignKey(name = "fk_administrative_division_timezone_division"))
    private AdministrativeArea administrativeDivision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timezone_id", nullable = false, foreignKey = @ForeignKey(name = "fk_administrative_division_timezone_timezone"))
    private TimezoneReference timezone;

    @Column(name = "default_timezone", nullable = false)
    private boolean defaultTimezone;

    protected AdministrativeDivisionTimezone() {
    }

    public AdministrativeArea getAdministrativeDivision() {
        return administrativeDivision;
    }

    public TimezoneReference getTimezone() {
        return timezone;
    }

    public boolean isDefaultTimezone() {
        return defaultTimezone;
    }
}
