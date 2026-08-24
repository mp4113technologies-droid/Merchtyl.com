package com.merchtyl.email;

public record EmailSendResult(
        boolean success,
        EmailProvider provider,
        String providerMessageId,
        String status,
        String failureCode,
        String failureMessage,
        boolean retryable
) {
    public static EmailSendResult accepted(EmailProvider provider, String providerMessageId) {
        return new EmailSendResult(true, provider, providerMessageId, "SENT", null, null, false);
    }

    public static EmailSendResult failed(EmailProvider provider, String code, String message, boolean retryable) {
        return new EmailSendResult(false, provider, null, retryable ? "RETRY_SCHEDULED" : "FAILED", code, message, retryable);
    }
}
