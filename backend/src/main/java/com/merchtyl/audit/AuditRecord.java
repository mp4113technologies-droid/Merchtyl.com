package com.merchtyl.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_records")
public class AuditRecord {
    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(updatable = false)
    private UUID actorUserId;

    @Column(updatable = false, nullable = false, length = 120)
    private String action;

    @Column(updatable = false, nullable = false, length = 120)
    private String entityType;

    @Column(updatable = false)
    private UUID entityId;

    @Column(updatable = false)
    private UUID storeId;

    @Column(updatable = false)
    private UUID registerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private String beforeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private String afterSnapshot;

    @Column(updatable = false, length = 1000)
    private String reason;

    @Column(updatable = false, length = 128)
    private String correlationId;

    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    protected AuditRecord() {
    }

    AuditRecord(
            UUID actorUserId,
            String action,
            String entityType,
            UUID entityId,
            UUID storeId,
            UUID registerId,
            String beforeSnapshot,
            String afterSnapshot,
            String reason,
            String correlationId) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.storeId = storeId;
        this.registerId = registerId;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.reason = reason;
        this.correlationId = correlationId;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getRegisterId() {
        return registerId;
    }

    public String getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
