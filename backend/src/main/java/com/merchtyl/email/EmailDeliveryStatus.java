package com.merchtyl.email;

public enum EmailDeliveryStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    RETRY_SCHEDULED,
    CANCELLED
}
