package com.merchtyl.idempotency;

public record IdempotencyOperationResponse(
        int status,
        String contentType,
        String body
) {
}
