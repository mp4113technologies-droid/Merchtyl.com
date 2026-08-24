package com.merchtyl.security;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "security_permissions",
        uniqueConstraints = @UniqueConstraint(name = "uq_security_permissions_code", columnNames = "code"))
public class Permission extends BaseUuidEntity {
    @Column(nullable = false, length = 120)
    private String code;

    @Column(length = 255)
    private String description;

    protected Permission() {
    }

    public Permission(String code, String description) {
        this.code = code;
        this.description = description;
        initializeIdAndTimestamps();
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
