package com.merchtyl.email;

import com.merchtyl.platform.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Service
public class PlatformAdminInvitationEmailService {
    private static final Logger log = LoggerFactory.getLogger(PlatformAdminInvitationEmailService.class);
    private final EmailSender emailSender;
    private final EmailProperties properties;
    private final EmailTemplateRenderer renderer;

    public PlatformAdminInvitationEmailService(EmailSender emailSender, EmailProperties properties, EmailTemplateRenderer renderer) {
        this.emailSender = emailSender;
        this.properties = properties;
        this.renderer = renderer;
    }

    @TransactionalEventListener
    public void send(PlatformAdminInvitationEmailEvent event) {
        String activationUrl = properties.frontendBaseUrl().replaceAll("/+$", "")
                + "/activate-platform-admin?token=" + event.rawToken();
        RenderedEmailTemplate rendered = renderer.render(EmailTemplateCode.PLATFORM_ADMIN_INVITATION, Map.of(
                "firstName", event.firstName(),
                "role", event.role().name().replace('_', ' '),
                "activationUrl", activationUrl,
                "expiresAt", event.expiresAt().toString()));
        EmailMessage message = new EmailMessage(
                List.of(new EmailRecipient(event.recipient(), event.firstName())),
                rendered.subject(), rendered.htmlBody(), rendered.textBody(),
                properties.fromAddress(), properties.fromName(), properties.replyTo(),
                EmailTemplateCode.PLATFORM_ADMIN_INVITATION,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("platformUserId", event.platformUserId().toString()));
        EmailSendResult result = emailSender.send(message);
        log.info("PLATFORM_ADMIN_INVITE_SENT targetPublicId={} targetRole={} provider={} accepted={}",
                event.platformUserId(), event.role(), emailSender.provider(), result.success());
    }
}
