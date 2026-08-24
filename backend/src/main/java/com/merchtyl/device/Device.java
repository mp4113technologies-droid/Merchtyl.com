package com.merchtyl.device;

import com.merchtyl.register.Register;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "devices",
        uniqueConstraints = @UniqueConstraint(name = "uq_devices_device_identifier", columnNames = "device_identifier"))
public class Device {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false)
    private Register register;

    @Column(nullable = false, length = 128)
    private String deviceIdentifier;

    @Column(nullable = false, length = 180)
    private String displayName;

    @Column(nullable = false, length = 64)
    private String deviceType;

    @Column(nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private boolean active;

    @Version
    private long version;

    protected Device() {
    }

    Device(
            Store store,
            Register register,
            String deviceIdentifier,
            String displayName,
            String deviceType,
            boolean active,
            Instant now) {
        this.store = store;
        this.register = register;
        this.deviceIdentifier = deviceIdentifier;
        this.displayName = displayName;
        this.deviceType = deviceType;
        this.active = active;
        this.registeredAt = now;
        this.lastSeenAt = now;
        this.id = UUID.randomUUID();
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (registeredAt == null) {
            registeredAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }

    void update(DeviceValues values) {
        this.store = values.store();
        this.register = values.register();
        this.deviceIdentifier = values.deviceIdentifier();
        this.displayName = values.displayName();
        this.deviceType = values.deviceType();
        this.active = values.active();
    }

    void setActive(boolean active) {
        this.active = active;
    }

    void touch(Instant now) {
        this.lastSeenAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public Register getRegister() {
        return register;
    }

    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
