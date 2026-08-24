package com.merchtyl.idempotency;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
class TestIdempotencyOperationRecorder {
    private final AtomicInteger operationCount = new AtomicInteger();

    int recordOperation() {
        return operationCount.incrementAndGet();
    }

    int operationCount() {
        return operationCount.get();
    }

    void reset() {
        operationCount.set(0);
    }
}
