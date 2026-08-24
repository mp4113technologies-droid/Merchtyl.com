package com.merchtyl.security;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "security_users",
        uniqueConstraints = @UniqueConstraint(name = "uq_security_users_email", columnNames = "email"))
public class User extends BaseUuidEntity {
    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_reason", length = 64)
    private AccountLockReason lockReason;

    @Column(name = "password_reset_at")
    private Instant passwordResetAt;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_role", length = 64)
    private RoleName createdByRole;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Column(name = "test_provisioned", nullable = false)
    private boolean testProvisioned;

    @Column(name = "test_provisioning_reference", length = 160)
    private String testProvisioningReference;

    @Column(name = "test_provisioned_at")
    private Instant testProvisionedAt;

    @Column(name = "temporary_password_issued_at")
    private Instant temporaryPasswordIssuedAt;

    @Column(name = "temporary_password_expires_at")
    private Instant temporaryPasswordExpiresAt;

    @Column(name = "first_login_at")
    private Instant firstLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "credentials_issued_at")
    private Instant credentialsIssuedAt;

    @Column(name = "credentials_delivery_status", length = 40)
    private String credentialsDeliveryStatus;

    protected User() {
    }

    public User(String email, String displayName, String passwordHash) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = true;
        this.locked = false;
        this.passwordChangeRequired = false;
        this.testProvisioned = false;
        initializeIdAndTimestamps();
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isLocked() {
        return locked;
    }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLastFailedLoginAt() { return lastFailedLoginAt; }
    public Instant getLockedAt() { return lockedAt; }
    public AccountLockReason getLockReason() { return lockReason; }
    public Instant getPasswordResetAt() { return passwordResetAt; }

    public UUID getTenantId() {
        return tenantId;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public RoleName getCreatedByRole() {
        return createdByRole;
    }

    public UUID getUpdatedByUserId() {
        return updatedByUserId;
    }

    public boolean isTestProvisioned() {
        return testProvisioned;
    }

    public String getTestProvisioningReference() {
        return testProvisioningReference;
    }

    public Instant getTestProvisionedAt() {
        return testProvisionedAt;
    }

    public Instant getTemporaryPasswordIssuedAt() {
        return temporaryPasswordIssuedAt;
    }

    public Instant getTemporaryPasswordExpiresAt() {
        return temporaryPasswordExpiresAt;
    }

    public Instant getFirstLoginAt() {
        return firstLoginAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Instant getCredentialsIssuedAt() {
        return credentialsIssuedAt;
    }

    public String getCredentialsDeliveryStatus() {
        return credentialsDeliveryStatus;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangeRequired = false;
        this.temporaryPasswordExpiresAt = null;
        this.passwordChangedAt = Instant.now();
        this.credentialsDeliveryStatus = null;
    }

    public void issueTemporaryPassword(String passwordHash, Instant issuedAt, Instant expiresAt) {
        this.passwordHash = passwordHash;
        this.enabled = true;
        this.passwordChangeRequired = true;
        this.temporaryPasswordIssuedAt = issuedAt;
        this.temporaryPasswordExpiresAt = expiresAt;
        this.credentialsIssuedAt = issuedAt;
        this.credentialsDeliveryStatus = "PENDING";
    }

    public void markFirstLogin(Instant firstLoginAt) {
        if (this.firstLoginAt == null) {
            this.firstLoginAt = firstLoginAt == null ? Instant.now() : firstLoginAt;
        }
    }

    public void assignTenant(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void markCreatedBy(UUID userId, RoleName role) {
        this.createdByUserId = userId;
        this.createdByRole = role;
        this.updatedByUserId = userId;
    }

    public void markUpdatedBy(UUID userId) {
        this.updatedByUserId = userId;
    }

    public void requirePasswordChange() {
        this.passwordChangeRequired = true;
    }

    public void setPasswordChangeRequired(boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public void markTestProvisioned(String reference, Instant provisionedAt) {
        this.testProvisioned = true;
        this.testProvisioningReference = reference;
        this.testProvisionedAt = provisionedAt == null ? Instant.now() : provisionedAt;
    }

    public void updateProfile(String email, String displayName, boolean locked) {
        this.email = email;
        this.displayName = displayName;
        this.locked = locked;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void lock() {
        this.locked = true;
    }

    public void unlock() {
        this.locked = false;
        this.failedLoginAttempts = 0;
        this.lockedAt = null;
        this.lockReason = null;
    }

    public void completePasswordReset(String passwordHash, Instant resetAt) {
        changePasswordHash(passwordHash);
        unlock();
        this.passwordResetAt = resetAt == null ? Instant.now() : resetAt;
    }
}
