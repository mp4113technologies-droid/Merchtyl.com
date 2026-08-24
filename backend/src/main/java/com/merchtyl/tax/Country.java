package com.merchtyl.tax;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.reference.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "countries")
public class Country extends BaseUuidEntity {
    @Column(nullable = false, unique = true, length = 2)
    private String code;

    @Column(name = "alpha3_code", nullable = false, unique = true, length = 3)
    private String alpha3Code;

    @Column(nullable = false, length = 180)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_currency_id", foreignKey = @ForeignKey(name = "fk_countries_default_currency"))
    private Currency defaultCurrency;

    @Column(name = "default_language_code", nullable = false, length = 16)
    private String defaultLanguageCode;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Country() {
    }

    public Country(String code, String name, boolean active) {
        this.code = code;
        this.alpha3Code = code;
        this.name = name;
        this.defaultLanguageCode = "en";
        this.active = active;
        initializeIdAndTimestamps();
    }

    public void update(String code, String name, boolean active) {
        this.code = code;
        this.name = name;
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getAlpha2Code() {
        return code;
    }

    public String getAlpha3Code() {
        return alpha3Code;
    }

    public String getName() {
        return name;
    }

    public Currency getDefaultCurrency() {
        return defaultCurrency;
    }

    public String getDefaultLanguageCode() {
        return defaultLanguageCode;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
