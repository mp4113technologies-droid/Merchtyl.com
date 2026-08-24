package com.merchtyl.email;

import java.time.Instant;
import java.util.UUID;

public record EmailDeliveryResponse(
        UUID id,
        UUID tenantId,
        UUID invitationId,
        String recipient,
        EmailTemplateCode templateCode,
        EmailProvider provider,
        String providerMessageId,
        EmailDeliveryStatus status,
        int attemptCount,
        Instant lastAttemptAt,
        Instant sentAt,
        Instant failedAt,
        Instant nextRetryAt,
        String failureCode,
        String failureMessageSanitized,
        String correlationId,
        java.util.UUID requestedByPlatformUserId,
        String requestedReason,
        String requestedNotes,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
