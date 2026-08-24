package com.merchtyl.register;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "registers",
        uniqueConstraints = @UniqueConstraint(name = "uq_registers_store_code", columnNames = {"store_id", "code"}))
public class Register extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 1000)
    private String locationDescription;

    @Column(nullable = false)
    private boolean active;

    protected Register() {
    }

    Register(Store store, String code, String name, String locationDescription, boolean active) {
        this.store = store;
        this.code = code;
        this.name = name;
        this.locationDescription = locationDescription;
        this.active = active;
        initializeIdAndTimestamps();
    }

    void update(RegisterValues values) {
        this.store = values.store();
        this.code = values.code();
        this.name = values.name();
        this.locationDescription = values.locationDescription();
        this.active = values.active();
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public Store getStore() {
        return store;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public boolean isActive() {
        return active;
    }
}
