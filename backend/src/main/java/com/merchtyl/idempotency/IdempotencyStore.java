package com.merchtyl.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyStore {
    void deleteExpired(UUID userId, String endpoint, String idempotencyKey, Instant now);

    Optional<IdempotencyRecord> findActiveForUpdate(UUID userId, String endpoint, String idempotencyKey, Instant now);

    IdempotencyRecord save(IdempotencyRecord record);

    long deleteExpiredBefore(Instant now);
}
