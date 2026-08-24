package com.merchtyl.security;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "security_user_store_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_security_user_store_assignments_tenant_user_store_role",
                columnNames = {"tenant_id", "user_id", "store_id", "assignment_role"}))
public class UserStoreAssignment extends BaseUuidEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_security_user_store_assignments_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_security_user_store_assignments_store"))
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 32)
    private AssignmentRole assignmentRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssignmentStatus status;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removal_reason", length = 1000)
    private String removalReason;

    protected UserStoreAssignment() {
    }

    public UserStoreAssignment(User user, Store store) {
        this(user.getTenantId(), user, store, AssignmentRole.CASHIER, user.getId());
    }

    public UserStoreAssignment(UUID tenantId, User user, Store store, AssignmentRole assignmentRole, UUID assignedBy) {
        if (tenantId == null && user != null) {
            tenantId = user.getTenantId();
        }
        this.user = user;
        this.store = store;
        this.tenantId = tenantId;
        this.assignmentRole = assignmentRole == null ? AssignmentRole.CASHIER : assignmentRole;
        this.status = AssignmentStatus.ACTIVE;
        this.active = true;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
        initializeIdAndTimestamps();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public User getUser() {
        return user;
    }

    public Store getStore() {
        return store;
    }

    public AssignmentRole getAssignmentRole() {
        return assignmentRole;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public UUID getRemovedBy() {
        return removedBy;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public String getRemovalReason() {
        return removalReason;
    }

    public void reactivate(AssignmentRole assignmentRole, UUID assignedBy) {
        this.assignmentRole = assignmentRole == null ? this.assignmentRole : assignmentRole;
        this.status = AssignmentStatus.ACTIVE;
        this.active = true;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
        this.removedBy = null;
        this.removedAt = null;
        this.removalReason = null;
    }

    public void changeRole(AssignmentRole assignmentRole) {
        this.assignmentRole = assignmentRole;
    }

    public void revoke(UUID removedBy, String reason) {
        this.status = AssignmentStatus.REVOKED;
        this.active = false;
        this.removedBy = removedBy;
        this.removedAt = Instant.now();
        this.removalReason = reason;
    }
}
