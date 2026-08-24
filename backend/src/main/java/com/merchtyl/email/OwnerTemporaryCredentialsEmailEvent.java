package com.merchtyl.email;

import java.time.Instant;
import java.util.UUID;

public record OwnerTemporaryCredentialsEmailEvent(
        UUID tenantId,
        String tenantCode,
        String merchantOperatingName,
        UUID ownerUserId,
        String recipient,
        String ownerName,
        String temporaryPassword,
        Instant expiresAt,
        EmailTemplateCode templateCode,
        UUID platformActorId,
        String reason,
        String notes
) {
}
