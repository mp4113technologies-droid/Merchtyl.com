package com.merchtyl.supplier;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "suppliers",
        uniqueConstraints = @UniqueConstraint(name = "uq_suppliers_code", columnNames = "code"))
public class Supplier extends BaseUuidEntity {
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 180)
    private String contactName;

    @Column(length = 40)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(length = 1000)
    private String address;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private boolean active;

    protected Supplier() {
    }

    public Supplier(SupplierValues values) {
        update(values);
        initializeIdAndTimestamps();
    }

    public void update(SupplierValues values) {
        this.code = values.code();
        this.name = values.name();
        this.contactName = values.contactName();
        this.phone = values.phone();
        this.email = values.email();
        this.address = values.address();
        this.notes = values.notes();
        this.active = values.active();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getContactName() {
        return contactName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }
}
