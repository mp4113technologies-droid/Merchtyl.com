package com.merchtyl.security;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "security_role_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_security_role_permissions_role_permission",
                columnNames = {"role_id", "permission_id"}))
public class RolePermission extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_security_role_permissions_role"))
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false, foreignKey = @ForeignKey(name = "fk_security_role_permissions_permission"))
    private Permission permission;

    protected RolePermission() {
    }

    public RolePermission(Role role, Permission permission) {
        this.role = role;
        this.permission = permission;
        initializeIdAndTimestamps();
    }

    public Role getRole() {
        return role;
    }

    public Permission getPermission() {
        return permission;
    }
}
