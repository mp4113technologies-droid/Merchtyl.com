package com.merchtyl.idempotency;

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
        name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_idempotency_records_scope",
                columnNames = {"user_id", "endpoint", "idempotency_key"}))
public class IdempotencyRecord extends BaseUuidEntity {
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String endpoint;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IdempotencyState state;

    private Integer responseStatus;

    @Column(length = 120)
    private String responseContentType;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Column(length = 1000)
    private String failureMessage;

    @Column(nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(
            String idempotencyKey,
            UUID userId,
            String endpoint,
            String requestFingerprint,
            Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.endpoint = endpoint;
        this.requestFingerprint = requestFingerprint;
        this.state = IdempotencyState.PROCESSING;
        this.expiresAt = expiresAt;
        initializeIdAndTimestamps();
    }

    public static IdempotencyRecord processing(
            String idempotencyKey,
            UUID userId,
            String endpoint,
            String requestFingerprint,
            Instant expiresAt) {
        return new IdempotencyRecord(idempotencyKey, userId, endpoint, requestFingerprint, expiresAt);
    }

    public void complete(IdempotencyOperationResponse response) {
        this.state = IdempotencyState.COMPLETED;
        this.responseStatus = response.status();
        this.responseContentType = response.contentType();
        this.responseBody = response.body();
        this.failureMessage = null;
    }

    public void fail(IdempotencyOperationResponse response, String failureMessage) {
        this.state = IdempotencyState.FAILED;
        this.responseStatus = response.status();
        this.responseContentType = response.contentType();
        this.responseBody = response.body();
        this.failureMessage = failureMessage;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public IdempotencyState getState() {
        return state;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
