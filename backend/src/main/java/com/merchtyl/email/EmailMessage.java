package com.merchtyl.email;

import java.util.List;
import java.util.Map;

public record EmailMessage(
        List<EmailRecipient> to,
        String subject,
        String htmlBody,
        String textBody,
        String fromAddress,
        String fromName,
        String replyTo,
        EmailTemplateCode templateCode,
        String correlationId,
        Map<String, String> metadata
) {
}
