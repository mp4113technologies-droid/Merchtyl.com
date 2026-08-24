package com.merchtyl.idempotency;

public record IdempotencyResult(
        IdempotencyState state,
        int status,
        String contentType,
        String body,
        boolean replayed
) {
    static IdempotencyResult fresh(IdempotencyRecord record) {
        return from(record, false);
    }

    static IdempotencyResult replayed(IdempotencyRecord record) {
        return from(record, true);
    }

    private static IdempotencyResult from(IdempotencyRecord record, boolean replayed) {
        return new IdempotencyResult(
                record.getState(),
                record.getResponseStatus(),
                record.getResponseContentType(),
                record.getResponseBody(),
                replayed);
    }
}
