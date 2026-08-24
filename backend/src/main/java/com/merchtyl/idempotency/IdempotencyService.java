package com.merchtyl.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final String DEFAULT_ERROR_CONTENT_TYPE = "application/json";
    private static final String DEFAULT_ERROR_BODY = "{\"code\":\"idempotent_operation_failed\"}";

    private final IdempotencyStore store;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public IdempotencyService(
            IdempotencyStore store,
            IdempotencyProperties properties,
            ObjectMapper objectMapper,
            TransactionOperations idempotencyTransactionOperations) {
        this(store, properties, objectMapper, idempotencyTransactionOperations, Clock.systemUTC());
    }

    IdempotencyService(
            IdempotencyStore store,
            IdempotencyProperties properties,
            ObjectMapper objectMapper,
            TransactionOperations transactions,
            Clock clock) {
        this.store = store;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.clock = clock;
    }

    public IdempotencyResult execute(
            UUID userId,
            String endpoint,
            String idempotencyKey,
            String requestBody,
            Supplier<IdempotencyOperationResponse> operation) {
        UUID normalizedUserId = Objects.requireNonNull(userId, "userId is required");
        String normalizedEndpoint = cleanRequired(endpoint, "endpoint");
        String normalizedKey = cleanIdempotencyKey(idempotencyKey);
        String fingerprint = fingerprint(requestBody);
        String lockKey = normalizedUserId + ":" + normalizedEndpoint + ":" + normalizedKey;
        Object lock = locks.computeIfAbsent(lockKey, ignored -> new Object());

        synchronized (lock) {
            try {
                return executeLocked(normalizedUserId, normalizedEndpoint, normalizedKey, fingerprint, operation);
            } finally {
                locks.remove(lockKey, lock);
            }
        }
    }

    public long deleteExpiredRecords() {
        return transactions.execute(status -> store.deleteExpiredBefore(clock.instant()));
    }

    private IdempotencyResult executeLocked(
            UUID userId,
            String endpoint,
            String idempotencyKey,
            String requestFingerprint,
            Supplier<IdempotencyOperationResponse> operation) {
        Instant now = clock.instant();
        IdempotencyRecord existing = transactions.execute(status -> {
            store.deleteExpired(userId, endpoint, idempotencyKey, now);
            return store.findActiveForUpdate(userId, endpoint, idempotencyKey, now).orElse(null);
        });
        if (existing != null) {
            return replayExisting(existing, requestFingerprint);
        }

        IdempotencyRecord processing = transactions.execute(status -> store.save(IdempotencyRecord.processing(
                idempotencyKey,
                userId,
                endpoint,
                requestFingerprint,
                now.plus(properties.getRetention()))));

        try {
            IdempotencyOperationResponse operationResponse = operation.get();
            IdempotencyRecord completed = transactions.execute(status -> {
                IdempotencyRecord record = store.findActiveForUpdate(userId, endpoint, idempotencyKey, clock.instant())
                        .orElse(processing);
                if (operationResponse.status() >= 500) {
                    record.fail(operationResponse, "Operation returned an error response");
                } else {
                    record.complete(operationResponse);
                }
                return store.save(record);
            });
            return IdempotencyResult.fresh(completed);
        } catch (RuntimeException exception) {
            transactions.execute(status -> {
                IdempotencyRecord record = store.findActiveForUpdate(userId, endpoint, idempotencyKey, clock.instant())
                        .orElse(processing);
                record.fail(new IdempotencyOperationResponse(
                                500,
                                DEFAULT_ERROR_CONTENT_TYPE,
                                DEFAULT_ERROR_BODY),
                        exception.getClass().getSimpleName());
                return store.save(record);
            });
            throw exception;
        }
    }

    private IdempotencyResult replayExisting(IdempotencyRecord existing, String requestFingerprint) {
        if (!existing.getRequestFingerprint().equals(requestFingerprint)) {
            throw new ConflictException("Idempotency key was already used with a different request payload");
        }
        if (existing.getState() == IdempotencyState.PROCESSING) {
            throw new ConflictException("Idempotent request is already processing");
        }
        return IdempotencyResult.replayed(existing);
    }

    private String cleanIdempotencyKey(String idempotencyKey) {
        String cleaned = cleanRequired(idempotencyKey, IDEMPOTENCY_KEY_HEADER);
        if (cleaned.length() > properties.getMaxKeyLength()) {
            throw new ForbiddenOperationException("Idempotency key is too long");
        }
        return cleaned;
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String fingerprint(String requestBody) {
        String normalized = normalizeRequestBody(requestBody);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private String normalizeRequestBody(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(requestBody));
        } catch (JsonProcessingException ignored) {
            return requestBody;
        }
    }
}
