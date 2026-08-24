package com.merchtyl.email;

import java.util.UUID;

public record MerchantNotificationEmailEvent(
        UUID tenantId,
        String tenantCode,
        String merchantOperatingName,
        String recipient,
        EmailTemplateCode templateCode,
        String reason,
        UUID platformActorId
) {
}
