package com.merchtyl.common;

import java.time.Duration;

public class TooManyRequestsException extends RuntimeException {
    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
