package com.merchtyl.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000b01");
    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-21T12:00:00Z"));
    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final IdempotencyProperties properties = new IdempotencyProperties();
    private final TransactionOperations transactions = new NoOpTransactionOperations();
    private final IdempotencyService service = new IdempotencyService(
            store,
            properties,
            new ObjectMapper(),
            transactions,
            clock);

    @Test
    void expiredKeyCanBeReusedAfterRetentionWindow() {
        properties.setRetention(Duration.ofSeconds(1));
        AtomicInteger operationCount = new AtomicInteger();

        IdempotencyResult first = execute("expiry-key", "{\"amount\":100}", operationCount);
        clock.advance(Duration.ofSeconds(2));
        IdempotencyResult second = execute("expiry-key", "{\"amount\":100}", operationCount);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isFalse();
        assertThat(operationCount.get()).isEqualTo(2);
    }

    @Test
    void keyScopeIncludesUserAndEndpoint() {
        AtomicInteger operationCount = new AtomicInteger();

        service.execute(
                USER_ID,
                "POST /sales",
                "scope-key",
                "{\"amount\":100}",
                () -> response(operationCount.incrementAndGet()));
        service.execute(
                USER_ID,
                "POST /refunds",
                "scope-key",
                "{\"amount\":100}",
                () -> response(operationCount.incrementAndGet()));
        service.execute(
                UUID.fromString("00000000-0000-0000-0000-000000000b02"),
                "POST /sales",
                "scope-key",
                "{\"amount\":100}",
                () -> response(operationCount.incrementAndGet()));

        assertThat(operationCount.get()).isEqualTo(3);
    }

    private IdempotencyResult execute(String key, String body, AtomicInteger operationCount) {
        return service.execute(
                USER_ID,
                "POST /sales",
                key,
                body,
                () -> response(operationCount.incrementAndGet()));
    }

    private static IdempotencyOperationResponse response(int operationCount) {
        return new IdempotencyOperationResponse(
                201,
                "application/json",
                "{\"operationCount\":" + operationCount + "}");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
