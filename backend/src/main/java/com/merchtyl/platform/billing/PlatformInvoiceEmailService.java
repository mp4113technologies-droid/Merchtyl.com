package com.merchtyl.platform.billing;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.email.EmailDeliveryStatus;
import com.merchtyl.email.EmailMessage;
import com.merchtyl.email.EmailProperties;
import com.merchtyl.email.EmailRecipient;
import com.merchtyl.email.EmailSendResult;
import com.merchtyl.email.EmailSender;
import com.merchtyl.email.EmailTemplateCode;
import com.merchtyl.email.EmailTemplateRenderer;
import com.merchtyl.platform.admin.PlatformUserRepository;
import com.merchtyl.platform.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformInvoiceEmailService {
    private static final Logger log = LoggerFactory.getLogger(PlatformInvoiceEmailService.class);
    private final PlatformBillingService billing;
    private final JdbcTemplate jdbc;
    private final EmailSender sender;
    private final EmailProperties properties;
    private final EmailTemplateRenderer renderer;
    private final PlatformUserRepository platformUsers;
    private final AuditService audit;

    public PlatformInvoiceEmailService(PlatformBillingService billing, JdbcTemplate jdbc, EmailSender sender,
                                       EmailProperties properties, EmailTemplateRenderer renderer,
                                       PlatformUserRepository platformUsers, AuditService audit) {
        this.billing = billing;
        this.jdbc = jdbc;
        this.sender = sender;
        this.properties = properties;
        this.renderer = renderer;
        this.platformUsers = platformUsers;
        this.audit = audit;
    }

    @Transactional
    public BillingDtos.InvoiceResponse send(UUID invoiceId, Authentication authentication) {
        return sendInternal(invoiceId, platformUsers.findByEmail(authentication.getName()).orElseThrow().id());
    }

    @Transactional
    public BillingDtos.InvoiceResponse sendSystem(UUID invoiceId) {
        return sendInternal(invoiceId, null);
    }

    private BillingDtos.InvoiceResponse sendInternal(UUID invoiceId, UUID actor) {
        BillingDtos.InvoiceResponse invoice = billing.invoice(invoiceId);
        UUID deliveryId = UUID.randomUUID();
        jdbc.update("""
                insert into email_deliveries(id,tenant_id,recipient,template_code,provider,status,attempt_count,correlation_id)
                values (?,?,?,?,?,'SENDING',1,?)
                """, deliveryId, invoice.tenantId(), invoice.billingEmail(), EmailTemplateCode.MERCHANT_SUBSCRIPTION_INVOICE.name(),
                sender.provider().name(), MDC.get(CorrelationIdFilter.MDC_KEY));
        UUID invoiceDeliveryId = UUID.randomUUID();
        jdbc.update("insert into platform_invoice_email_deliveries(id,invoice_id,email_delivery_id,status,attempt_count) values (?,?,?,'SENDING',1)",
                invoiceDeliveryId, invoiceId, deliveryId);
        String invoiceUrl = properties.frontendBaseUrl().replaceAll("/+$", "") + "/billing/invoices/" + invoiceId;
        var rendered = renderer.render(EmailTemplateCode.MERCHANT_SUBSCRIPTION_INVOICE, Map.of(
                "merchantName", invoice.merchantName(),
                "invoiceNumber", invoice.invoiceNumber(),
                "billingPeriod", invoice.billingPeriodStart() + " – " + invoice.billingPeriodEnd(),
                "amountDue", invoice.amountOutstanding() + " " + invoice.currency(),
                "dueDate", invoice.dueDate().toString(),
                "invoiceUrl", invoiceUrl));
        EmailSendResult result = sender.send(new EmailMessage(
                List.of(new EmailRecipient(invoice.billingEmail(), invoice.merchantName())),
                "Merchtyl Invoice " + invoice.invoiceNumber(), rendered.htmlBody(), rendered.textBody(),
                properties.fromAddress(), properties.fromName(), properties.replyTo(),
                EmailTemplateCode.MERCHANT_SUBSCRIPTION_INVOICE, MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("invoiceNumber", invoice.invoiceNumber())));
        if (result.success()) {
            jdbc.update("update email_deliveries set status='SENT',provider_message_id=?,sent_at=now(),updated_at=now(),version=version+1 where id=?", result.providerMessageId(), deliveryId);
            jdbc.update("update platform_invoice_email_deliveries set status='SENT',sent_at=now(),updated_at=now() where id=?", invoiceDeliveryId);
            jdbc.update("update platform_invoices set status=case when status='ISSUED' then 'SENT' else status end,sent_at=now(),updated_at=now(),version=version+1 where id=?", invoiceId);
            if (actor != null) {
                audit.record(new CreateAuditRecordCommand(actor, invoice.sentAt() == null ? AuditAction.PLATFORM_INVOICE_SENT : AuditAction.PLATFORM_INVOICE_RESENT,
                        "PLATFORM_INVOICE", invoiceId, null, null, invoice, billing.invoice(invoiceId), "Invoice email accepted by provider"));
            }
            log.info("billing_event event=SUBSCRIPTION_INVOICE_EMAIL_SENT tenant_id={} subscription_public_id={} invoice_public_id={} invoice_number={}", invoice.tenantId(), invoice.subscriptionId(), invoice.id(), invoice.invoiceNumber());
        } else {
            EmailDeliveryStatus status = result.retryable() ? EmailDeliveryStatus.RETRY_SCHEDULED : EmailDeliveryStatus.FAILED;
            jdbc.update("update email_deliveries set status=?,failure_code=?,failure_message_sanitized=?,failed_at=now(),updated_at=now(),version=version+1 where id=?",
                    status.name(), result.failureCode(), clean(result.failureMessage()), deliveryId);
            jdbc.update("update platform_invoice_email_deliveries set status=?,failure_code=?,updated_at=now() where id=?", status.name(), result.failureCode(), invoiceDeliveryId);
            log.warn("billing_event event=SUBSCRIPTION_INVOICE_EMAIL_FAILED tenant_id={} subscription_public_id={} invoice_public_id={} invoice_number={} failure_code={}", invoice.tenantId(), invoice.subscriptionId(), invoice.id(), invoice.invoiceNumber(), result.failureCode());
        }
        return billing.invoice(invoiceId);
    }

    private static String clean(String value) {
        if (value == null) return null;
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
