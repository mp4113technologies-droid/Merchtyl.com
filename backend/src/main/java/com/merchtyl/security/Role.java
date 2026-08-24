package com.merchtyl.security;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "security_roles",
        uniqueConstraints = @UniqueConstraint(name = "uq_security_roles_name", columnNames = "name"))
public class Role extends BaseUuidEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private RoleName name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean systemRole;

    protected Role() {
    }

    public Role(RoleName name, String description, boolean systemRole) {
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
        initializeIdAndTimestamps();
    }

    public RoleName getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }
}
