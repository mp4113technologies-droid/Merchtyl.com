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
        name = "security_user_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_security_user_roles_user_role",
                columnNames = {"user_id", "role_id"}))
public class UserRole extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_security_user_roles_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_security_user_roles_role"))
    private Role role;

    protected UserRole() {
    }

    public UserRole(User user, Role role) {
        this.user = user;
        this.role = role;
        initializeIdAndTimestamps();
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }
}
