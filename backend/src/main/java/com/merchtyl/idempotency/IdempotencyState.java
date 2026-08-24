package com.merchtyl.idempotency;

public enum IdempotencyState {
    PROCESSING,
    COMPLETED,
    FAILED
}
