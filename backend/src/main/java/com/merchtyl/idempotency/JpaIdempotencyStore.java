package com.merchtyl.idempotency;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaIdempotencyStore implements IdempotencyStore {
    private final IdempotencyRecordRepository repository;

    public JpaIdempotencyStore(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public void deleteExpired(UUID userId, String endpoint, String idempotencyKey, Instant now) {
        repository.deleteByUserIdAndEndpointAndIdempotencyKeyAndExpiresAtBefore(userId, endpoint, idempotencyKey, now);
    }

    @Override
    public Optional<IdempotencyRecord> findActiveForUpdate(
            UUID userId,
            String endpoint,
            String idempotencyKey,
            Instant now) {
        return repository.findByUserIdAndEndpointAndIdempotencyKeyAndExpiresAtAfter(
                userId,
                endpoint,
                idempotencyKey,
                now);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        return repository.saveAndFlush(record);
    }

    @Override
    public long deleteExpiredBefore(Instant now) {
        return repository.deleteByExpiresAtBefore(now);
    }
}
