package com.merchtyl.security;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "security_user_register_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_security_user_register_assignments_user_register",
                columnNames = {"user_id", "register_id"}))
public class UserRegisterAssignment extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_security_user_register_assignments_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, foreignKey = @ForeignKey(name = "fk_security_user_register_assignments_register"))
    private Register register;

    protected UserRegisterAssignment() {
    }

    public UserRegisterAssignment(User user, Register register) {
        this.user = user;
        this.register = register;
        initializeIdAndTimestamps();
    }

    public User getUser() {
        return user;
    }

    public Register getRegister() {
        return register;
    }
}
