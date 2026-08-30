package com.merchtyl.store;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "stores",
        uniqueConstraints = @UniqueConstraint(name = "uq_stores_code", columnNames = "code"))
public class Store extends BaseUuidEntity {
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "administrative_area_code", length = 32)
    private String administrativeAreaCode;

    @Column(name = "administrative_division_id")
    private UUID administrativeDivisionId;

    @Column(nullable = false, length = 1000)
    private String address;

    @Column(length = 40)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "currency_id")
    private UUID currencyId;

    @Column(nullable = false, length = 35)
    private String locale;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "timezone_id")
    private UUID timezoneId;

    @Column(name = "timezone_name", length = 64)
    private String timezoneName;

    @Column(name = "tax_region_id")
    private UUID taxRegionId;

    @Column(name = "tax_region_code", length = 64)
    private String taxRegionCode;

    @Column(name = "prices_include_tax", nullable = false)
    private boolean pricesIncludeTax;

    @Column(name = "negative_stock_allowed", nullable = false)
    private boolean negativeStockAllowed;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "store_capabilities", joinColumns = @JoinColumn(name = "store_id"))
    @Column(name = "capability", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private Set<StoreCapability> capabilities = new LinkedHashSet<>();

    @Column(name = "kitchen_display_name", length = 180)
    private String kitchenDisplayName;

    protected Store() {
    }

    Store(
            String code,
            String name,
            String legalName,
            String countryCode,
            String administrativeAreaCode,
            String address,
            String phone,
            String email,
            String currencyCode,
            String locale,
            String timezone,
            boolean pricesIncludeTax,
            boolean negativeStockAllowed,
            boolean active) {
        this(
                code,
                name,
                legalName,
                countryCode,
                null,
                administrativeAreaCode,
                null,
                address,
                phone,
                email,
                currencyCode,
                null,
                locale,
                timezone,
                null,
                timezone,
                null,
                null,
                pricesIncludeTax,
                negativeStockAllowed,
                active);
    }

    Store(
            String code,
            String name,
            String legalName,
            String countryCode,
            UUID countryId,
            String administrativeAreaCode,
            UUID administrativeDivisionId,
            String address,
            String phone,
            String email,
            String currencyCode,
            UUID currencyId,
            String locale,
            String timezone,
            UUID timezoneId,
            String timezoneName,
            UUID taxRegionId,
            String taxRegionCode,
            boolean pricesIncludeTax,
            boolean negativeStockAllowed,
            boolean active) {
        this.code = code;
        this.name = name;
        this.legalName = legalName;
        this.countryCode = countryCode;
        this.countryId = countryId;
        this.administrativeAreaCode = administrativeAreaCode;
        this.administrativeDivisionId = administrativeDivisionId;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.currencyCode = currencyCode;
        this.currencyId = currencyId;
        this.locale = locale;
        this.timezone = timezone;
        this.timezoneId = timezoneId;
        this.timezoneName = timezoneName;
        this.taxRegionId = taxRegionId;
        this.taxRegionCode = taxRegionCode;
        this.pricesIncludeTax = pricesIncludeTax;
        this.negativeStockAllowed = negativeStockAllowed;
        this.active = active;
        initializeIdAndTimestamps();
    }

    void update(StoreValues values) {
        this.code = values.code();
        this.name = values.name();
        this.legalName = values.legalName();
        this.countryCode = values.countryCode();
        this.countryId = values.countryId();
        this.administrativeAreaCode = values.administrativeAreaCode();
        this.administrativeDivisionId = values.administrativeDivisionId();
        this.address = values.address();
        this.phone = values.phone();
        this.email = values.email();
        this.currencyCode = values.currencyCode();
        this.currencyId = values.currencyId();
        this.locale = values.locale();
        this.timezone = values.timezone();
        this.timezoneId = values.timezoneId();
        this.timezoneName = values.timezoneName();
        this.taxRegionId = values.taxRegionId();
        this.taxRegionCode = values.taxRegionCode();
        this.pricesIncludeTax = values.pricesIncludeTax();
        this.negativeStockAllowed = values.negativeStockAllowed();
        this.active = values.active();
    }

    void setActive(boolean active) {
        this.active = active;
    }

    void configureOperations(Set<StoreCapability> capabilities, String kitchenDisplayName) {
        this.capabilities.clear();
        this.capabilities.addAll(capabilities);
        this.kitchenDisplayName = capabilities.contains(StoreCapability.FOOD_SERVICE) ? kitchenDisplayName : null;
    }

    public Set<StoreCapability> getCapabilities() { return Set.copyOf(capabilities); }
    public String getKitchenDisplayName() { return kitchenDisplayName; }
    public boolean isFoodServiceEnabled() { return capabilities.contains(StoreCapability.FOOD_SERVICE); }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public UUID getCountryId() {
        return countryId;
    }

    public String getAdministrativeAreaCode() {
        return administrativeAreaCode;
    }

    public UUID getAdministrativeDivisionId() {
        return administrativeDivisionId;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public UUID getCurrencyId() {
        return currencyId;
    }

    public String getLocale() {
        return locale;
    }

    public String getTimezone() {
        return timezone;
    }

    public UUID getTimezoneId() {
        return timezoneId;
    }

    public String getTimezoneName() {
        return timezoneName;
    }

    public UUID getTaxRegionId() {
        return taxRegionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void assignTenant(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTaxRegionCode() {
        return taxRegionCode;
    }

    public boolean isPricesIncludeTax() {
        return pricesIncludeTax;
    }

    public boolean isNegativeStockAllowed() {
        return negativeStockAllowed;
    }

    public boolean isActive() {
        return active;
    }
}
