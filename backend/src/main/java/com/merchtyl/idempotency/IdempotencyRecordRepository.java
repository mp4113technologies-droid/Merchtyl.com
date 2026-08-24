package com.merchtyl.idempotency;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdempotencyRecord> findByUserIdAndEndpointAndIdempotencyKeyAndExpiresAtAfter(
            UUID userId,
            String endpoint,
            String idempotencyKey,
            Instant now);

    @Modifying
    long deleteByUserIdAndEndpointAndIdempotencyKeyAndExpiresAtBefore(
            UUID userId,
            String endpoint,
            String idempotencyKey,
            Instant now);

    @Modifying
    long deleteByExpiresAtBefore(Instant now);
}
