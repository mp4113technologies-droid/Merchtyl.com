package com.merchtyl.idempotency;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class InMemoryIdempotencyStore implements IdempotencyStore {
    private final Map<String, IdempotencyRecord> records = new HashMap<>();

    @Override
    public synchronized void deleteExpired(UUID userId, String endpoint, String idempotencyKey, Instant now) {
        records.computeIfPresent(scope(userId, endpoint, idempotencyKey),
                (ignored, record) -> record.getExpiresAt().isBefore(now) ? null : record);
    }

    @Override
    public synchronized Optional<IdempotencyRecord> findActiveForUpdate(
            UUID userId,
            String endpoint,
            String idempotencyKey,
            Instant now) {
        IdempotencyRecord record = records.get(scope(userId, endpoint, idempotencyKey));
        if (record == null || !record.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    @Override
    public synchronized IdempotencyRecord save(IdempotencyRecord record) {
        records.put(scope(record.getUserId(), record.getEndpoint(), record.getIdempotencyKey()), record);
        return record;
    }

    @Override
    public synchronized long deleteExpiredBefore(Instant now) {
        long deleted = 0;
        Iterator<IdempotencyRecord> iterator = records.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getExpiresAt().isBefore(now)) {
                iterator.remove();
                deleted++;
            }
        }
        return deleted;
    }

    private static String scope(UUID userId, String endpoint, String idempotencyKey) {
        return userId + "|" + endpoint + "|" + idempotencyKey;
    }
}
