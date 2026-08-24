package com.merchtyl.email;

import java.time.Instant;
import java.util.UUID;

public record OwnerInvitationEmailEvent(
        UUID tenantId,
        String tenantCode,
        String merchantOperatingName,
        UUID invitationId,
        String recipient,
        String ownerName,
        String rawToken,
        Instant expiresAt,
        EmailTemplateCode templateCode,
        UUID platformActorId,
        String reason,
        String notes
) {
}
