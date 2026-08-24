package com.merchtyl.reference;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "currencies")
public class Currency extends BaseUuidEntity {
    @Column(nullable = false, unique = true, length = 3)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 8)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private int decimalPlaces;

    @Column(nullable = false)
    private boolean active;

    protected Currency() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public boolean isActive() {
        return active;
    }
}
