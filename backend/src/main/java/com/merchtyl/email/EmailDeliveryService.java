package com.merchtyl.email;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.config.SecurityProperties;
import com.merchtyl.config.PlatformAdministrationProperties;
import com.merchtyl.platform.web.CorrelationIdFilter;
import com.merchtyl.security.TemporaryPasswordGenerator;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class EmailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ConcurrentMap<UUID, String> TRANSIENT_INVITATION_TOKENS = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbcTemplate;
    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final PlatformAdministrationProperties platformProperties;
    private final EmailTemplateRenderer templateRenderer;
    private final AuditService auditService;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    public EmailDeliveryService(
            JdbcTemplate jdbcTemplate,
            EmailSender emailSender,
            EmailProperties emailProperties,
            PlatformAdministrationProperties platformProperties,
            EmailTemplateRenderer templateRenderer,
            AuditService auditService,
            TemporaryPasswordGenerator temporaryPasswordGenerator,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.platformProperties = platformProperties;
        this.templateRenderer = templateRenderer;
        this.auditService = auditService;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendOwnerInvitationAfterCommit(OwnerInvitationEmailEvent event) {
        try {
            UUID deliveryId = createDelivery(event.tenantId(), event.invitationId(), event.recipient(), event.templateCode(),
                    event.platformActorId(), event.reason(), event.notes());
            audit(event.platformActorId(), AuditAction.ACTIVATION_EMAIL_QUEUED, deliveryId, event.tenantId(), event.recipient(), null);
            sendOwnerInvitationDelivery(deliveryId, event, event.rawToken());
        } catch (RuntimeException exception) {
            log.error("Failed to process owner invitation email event for tenant {}", event.tenantId(), exception);
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendOwnerTemporaryCredentialsAfterCommit(OwnerTemporaryCredentialsEmailEvent event) {
        try {
            UUID deliveryId = createDelivery(event.tenantId(), null, event.recipient(), event.templateCode(),
                    event.platformActorId(), event.reason(), event.notes());
            jdbcTemplate.update("""
                    update security_users
                    set credentials_delivery_status = 'PENDING', updated_at = now(), version = version + 1
                    where id = ?
                    """, event.ownerUserId());
            audit(event.platformActorId(), AuditAction.TEMPORARY_OWNER_CREDENTIALS_EMAIL_QUEUED, deliveryId, event.tenantId(), event.recipient(), null);
            sendOwnerTemporaryCredentialsDelivery(deliveryId, event);
        } catch (RuntimeException exception) {
            log.error("Failed to process owner temporary credentials email event for tenant {}", event.tenantId(), exception);
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendMerchantNotificationAfterCommit(MerchantNotificationEmailEvent event) {
        try {
            UUID deliveryId = createDelivery(event.tenantId(), null, event.recipient(), event.templateCode(),
                    event.platformActorId(), event.reason(), null);
            audit(event.platformActorId(), AuditAction.MERCHANT_NOTIFICATION_EMAIL_QUEUED, deliveryId, event.tenantId(), event.recipient(), null);
            sendMerchantNotificationDelivery(deliveryId, event);
        } catch (RuntimeException exception) {
            log.error("Failed to process merchant notification email event for tenant {}", event.tenantId(), exception);
        }
    }

    @Transactional(readOnly = true)
    public List<EmailDeliveryResponse> listTenantDeliveries(UUID tenantId) {
        return jdbcTemplate.query("""
                select * from email_deliveries
                where tenant_id = ?
                order by created_at desc, id desc
                """, mapper(), tenantId);
    }

    @Transactional(readOnly = true)
    public EmailDeliveryResponse getDelivery(UUID deliveryId) {
        return jdbcTemplate.query("""
                select * from email_deliveries where id = ?
                """, mapper(), deliveryId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Email delivery not found"));
    }

    @Transactional
    public EmailDeliveryResponse retryDelivery(UUID deliveryId, UUID actorId) {
        EmailDeliveryResponse delivery = getDelivery(deliveryId);
        log.info("email_event event=Email Retried delivery_id={} provider={} recipient={} template={} tenant_id={} attempt_count={}",
                deliveryId,
                delivery.provider(),
                delivery.recipient(),
                delivery.templateCode(),
                delivery.tenantId(),
                delivery.attemptCount() + 1);
        if (delivery.status() == EmailDeliveryStatus.SENT) {
            throw new ConflictException("Email delivery has already been accepted by the provider");
        }
        if (delivery.attemptCount() >= emailProperties.maxAttempts()) {
            throw new ConflictException("Email delivery has reached the maximum retry count");
        }
        if (delivery.invitationId() == null) {
            if (passwordReset(delivery.templateCode())) {
                throw new ConflictException("Request a new password reset link; reset tokens are not retained for email retry");
            }
            if (temporaryCredentials(delivery.templateCode())) {
                return reissueTemporaryCredentialsForRetry(delivery, actorId);
            }
            if (delivery.templateCode() != EmailTemplateCode.TEST_EMAIL) {
                return resendMerchantNotification(delivery, actorId);
            }
            return sendTestDelivery(deliveryId, delivery.recipient(), actorId);
        }
        Map<String, Object> invitation = jdbcTemplate.queryForMap("""
                select invitation.id, invitation.tenant_id, invitation.owner_user_id, invitation.email,
                       invitation.status, invitation.expires_at,
                       tenant.tenant_code, tenant.display_name, users.display_name as owner_name
                from tenant_owner_invitations invitation
                join tenants tenant on tenant.id = invitation.tenant_id
                join security_users users on users.id = invitation.owner_user_id
                where invitation.id = ?
                """, delivery.invitationId());
        String status = (String) invitation.get("status");
        Instant expiresAt = instant(invitation.get("expires_at"));
        if (!List.of("PENDING", "SENT").contains(status) || !expiresAt.isAfter(Instant.now())) {
            throw new ConflictException("Current activation invitation is no longer valid; resend a new activation link");
        }
        if (successfulDeliveryExists(delivery.invitationId())) {
            throw new ConflictException("Email delivery has already been accepted by the provider");
        }
        String rawToken = TRANSIENT_INVITATION_TOKENS.get(delivery.invitationId());
        if (rawToken == null || rawToken.isBlank()) {
            throw new ConflictException("Activation link is unavailable for retry; resend a new activation link");
        }
        audit(actorId, AuditAction.ACTIVATION_EMAIL_RETRIED, deliveryId, delivery.tenantId(), delivery.recipient(), null);
        sendOwnerInvitationDelivery(deliveryId, new OwnerInvitationEmailEvent(
                (UUID) invitation.get("tenant_id"),
                (String) invitation.get("tenant_code"),
                (String) invitation.get("display_name"),
                (UUID) invitation.get("id"),
                (String) invitation.get("email"),
                (String) invitation.get("owner_name"),
                rawToken,
                expiresAt,
                delivery.templateCode(),
                actorId,
                "Retry failed activation email delivery",
                null), rawToken);
        return getDelivery(deliveryId);
    }

    private EmailDeliveryResponse reissueTemporaryCredentialsForRetry(EmailDeliveryResponse failedDelivery, UUID actorId) {
        if (failedDelivery.tenantId() == null) {
            throw new ConflictException("Temporary credentials delivery is not linked to a tenant");
        }
        Map<String, Object> owner = jdbcTemplate.queryForMap("""
                select users.id as owner_user_id, users.email, users.display_name as owner_name,
                       users.password_change_required, tenants.tenant_code, tenants.display_name as merchant_name,
                       tenants.status as tenant_status
                from security_users users
                join tenants tenants on tenants.id = users.tenant_id
                where users.tenant_id = ? and lower(users.email) = lower(?)
                """, failedDelivery.tenantId(), failedDelivery.recipient());
        String tenantStatus = (String) owner.get("tenant_status");
        if (List.of("CLOSED", "SUSPENDED", "REJECTED").contains(tenantStatus)) {
            throw new ConflictException("Temporary credentials cannot be reissued while tenant status is " + tenantStatus);
        }
        Boolean passwordChangeRequired = (Boolean) owner.get("password_change_required");
        if (!Boolean.TRUE.equals(passwordChangeRequired)) {
            throw new ConflictException("Merchant owner has already completed first-login password change.");
        }

        UUID ownerUserId = (UUID) owner.get("owner_user_id");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(securityProperties.temporaryPassword().expiry());
        String temporaryPassword = temporaryPasswordGenerator.generate();
        jdbcTemplate.update("""
                update security_users
                set password_hash = ?, enabled = true, password_change_required = true,
                    temporary_password_issued_at = ?, temporary_password_expires_at = ?,
                    credentials_issued_at = ?, credentials_delivery_status = 'PENDING',
                    updated_at = now(), version = version + 1
                where id = ?
                """,
                passwordEncoder.encode(temporaryPassword),
                timestamp(now),
                timestamp(expiresAt),
                timestamp(now),
                ownerUserId);
        jdbcTemplate.update("""
                update security_refresh_tokens
                set revoked_at = ?, updated_at = now(), version = version + 1
                where user_id = ? and revoked_at is null and expires_at > ?
                """, timestamp(now), ownerUserId, timestamp(now));
        jdbcTemplate.update("""
                update first_login_password_change_tokens
                set revoked_at = ?, updated_at = now(), version = version + 1
                where user_id = ? and used_at is null and revoked_at is null
                """, timestamp(now), ownerUserId);

        UUID newDeliveryId = createDelivery(
                failedDelivery.tenantId(),
                null,
                failedDelivery.recipient(),
                EmailTemplateCode.MERCHANT_OWNER_TEMPORARY_CREDENTIALS_RESEND,
                actorId,
                "Retry failed temporary credentials delivery by reissue",
                failedDelivery.requestedNotes());
        audit(actorId, AuditAction.TEMPORARY_CREDENTIALS_REISSUED, newDeliveryId, failedDelivery.tenantId(), failedDelivery.recipient(),
                Map.of("ownerUserId", String.valueOf(ownerUserId), "expiresAt", expiresAt.toString(), "retryOfDeliveryId", failedDelivery.id().toString()));
        sendOwnerTemporaryCredentialsDelivery(newDeliveryId, new OwnerTemporaryCredentialsEmailEvent(
                failedDelivery.tenantId(),
                (String) owner.get("tenant_code"),
                (String) owner.get("merchant_name"),
                ownerUserId,
                failedDelivery.recipient(),
                (String) owner.get("owner_name"),
                temporaryPassword,
                expiresAt,
                EmailTemplateCode.MERCHANT_OWNER_TEMPORARY_CREDENTIALS_RESEND,
                actorId,
                "Retry failed temporary credentials delivery by reissue",
                failedDelivery.requestedNotes()));
        return getDelivery(newDeliveryId);
    }

    @Transactional
    public EmailDeliveryResponse sendTestEmail(String recipient, UUID actorId) {
        UUID deliveryId = createDelivery(null, null, recipient, EmailTemplateCode.TEST_EMAIL, actorId,
                "Platform test email requested", null);
        audit(actorId, AuditAction.TEST_EMAIL_REQUESTED, deliveryId, null, recipient, null);
        return sendTestDelivery(deliveryId, recipient, actorId);
    }

    @Transactional(readOnly = true)
    public EmailProviderStatusResponse providerStatus() {
        boolean fromConfigured = emailProperties.fromAddress() != null && !emailProperties.fromAddress().isBlank();
        boolean configured = emailSender.provider() == EmailProvider.CONSOLE
                || (emailProperties.resend().enabled()
                && emailProperties.resend().apiKey() != null
                && !emailProperties.resend().apiKey().isBlank()
                && fromConfigured);
        return new EmailProviderStatusResponse(emailSender.provider(), configured, emailProperties.resend().enabled(), fromConfigured);
    }

    @Transactional
    public EmailDeliveryResponse sendPasswordReset(UUID tenantId, String recipient, String displayName,
                                                   String rawToken, Instant expiresAt, UUID actorId, String reason) {
        UUID deliveryId = createDelivery(tenantId, null, recipient, EmailTemplateCode.PASSWORD_RESET, actorId, reason, null);
        String resetUrl = emailProperties.passwordResetUrl(rawToken);
        RenderedEmailTemplate rendered = templateRenderer.render(EmailTemplateCode.PASSWORD_RESET, Map.of(
                "recipient", displayName == null || displayName.isBlank() ? recipient : displayName,
                "resetUrl", resetUrl,
                "expiresAt", expiresAt.toString()));
        send(deliveryId, message(List.of(new EmailRecipient(recipient, displayName)), rendered,
                EmailTemplateCode.PASSWORD_RESET, Map.of()), actorId);
        return getDelivery(deliveryId);
    }

    private EmailDeliveryResponse sendTestDelivery(UUID deliveryId, String recipient, UUID actorId) {
        RenderedEmailTemplate rendered = templateRenderer.render(EmailTemplateCode.TEST_EMAIL, Map.of("recipient", recipient));
        EmailMessage message = message(List.of(new EmailRecipient(recipient, null)), rendered, EmailTemplateCode.TEST_EMAIL, Map.of());
        send(deliveryId, message, actorId);
        return getDelivery(deliveryId);
    }

    private void sendOwnerInvitationDelivery(UUID deliveryId, OwnerInvitationEmailEvent event, String rawToken) {
        TRANSIENT_INVITATION_TOKENS.put(event.invitationId(), rawToken);
        String activationUrl = emailProperties.activationUrl(rawToken);
        RenderedEmailTemplate rendered = templateRenderer.render(event.templateCode(), Map.of(
                "merchantOperatingName", event.merchantOperatingName(),
                "ownerName", event.ownerName(),
                "activationUrl", activationUrl,
                "expiresAt", event.expiresAt().toString()));
        EmailMessage message = message(
                List.of(new EmailRecipient(event.recipient(), event.ownerName())),
                rendered,
                event.templateCode(),
                Map.of("activationUrl", activationUrl, "tenantCode", event.tenantCode()));
        send(deliveryId, message, event.platformActorId());
    }

    private void sendOwnerTemporaryCredentialsDelivery(UUID deliveryId, OwnerTemporaryCredentialsEmailEvent event) {
        String loginUrl = emailProperties.frontendBaseUrl().replaceAll("/+$", "") + "/login";
        RenderedEmailTemplate rendered = templateRenderer.render(event.templateCode(), Map.of(
                "merchantOperatingName", event.merchantOperatingName(),
                "ownerName", event.ownerName(),
                "loginEmail", event.recipient(),
                "temporaryPassword", event.temporaryPassword(),
                "loginUrl", loginUrl,
                "expiresAt", event.expiresAt().toString()));
        EmailMessage message = message(
                List.of(new EmailRecipient(event.recipient(), event.ownerName())),
                rendered,
                event.templateCode(),
                Map.of("tenantCode", event.tenantCode()));
        send(deliveryId, message, event.platformActorId());
        EmailDeliveryResponse delivery = getDelivery(deliveryId);
        jdbcTemplate.update("""
                update security_users
                set credentials_delivery_status = ?, updated_at = now(), version = version + 1
                where id = ?
                """, delivery.status().name(), event.ownerUserId());
    }

    private EmailDeliveryResponse resendMerchantNotification(EmailDeliveryResponse delivery, UUID actorId) {
        Map<String, Object> tenant = jdbcTemplate.queryForMap("""
                select tenant_code, display_name from tenants where id = ?
                """, delivery.tenantId());
        sendMerchantNotificationDelivery(delivery.id(), new MerchantNotificationEmailEvent(
                delivery.tenantId(),
                (String) tenant.get("tenant_code"),
                (String) tenant.get("display_name"),
                delivery.recipient(),
                delivery.templateCode(),
                "See platform audit history",
                actorId));
        return getDelivery(delivery.id());
    }

    private void sendMerchantNotificationDelivery(UUID deliveryId, MerchantNotificationEmailEvent event) {
        RenderedEmailTemplate rendered = templateRenderer.render(event.templateCode(), Map.of(
                "merchantOperatingName", event.merchantOperatingName(),
                "reason", event.reason() == null || event.reason().isBlank() ? "Not specified" : event.reason()));
        EmailMessage message = message(
                List.of(new EmailRecipient(event.recipient(), null)),
                rendered,
                event.templateCode(),
                Map.of("tenantCode", event.tenantCode()));
        send(deliveryId, message, event.platformActorId());
    }

    private void send(UUID deliveryId, EmailMessage message, UUID actorId) {
        EmailDeliveryResponse before = getDelivery(deliveryId);
        int attempt = before.attemptCount() + 1;
        long started = System.nanoTime();
        jdbcTemplate.update("""
                update email_deliveries
                set status = 'SENDING', attempt_count = ?, last_attempt_at = now(),
                    failure_code = null, failure_message_sanitized = null,
                    updated_at = now(), version = version + 1
                where id = ?
                """, attempt, deliveryId);
        EmailSendResult result = emailSender.send(message);
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        if (result.success()) {
            jdbcTemplate.update("""
                    update email_deliveries
                    set status = 'SENT', provider = ?, provider_message_id = ?, sent_at = now(), failed_at = null,
                        next_retry_at = null, updated_at = now(), version = version + 1
                    where id = ?
                    """, result.provider().name(), result.providerMessageId(), deliveryId);
            if (before.invitationId() != null) {
                jdbcTemplate.update("""
                        update tenant_owner_invitations
                        set status = case when status = 'PENDING' then 'SENT' else status end,
                            updated_at = now(), version = version + 1
                        where id = ?
                        """, before.invitationId());
            }
            if (temporaryCredentials(before.templateCode())) {
                jdbcTemplate.update("""
                        update security_users
                        set credentials_delivery_status = 'SENT', updated_at = now(), version = version + 1
                        where tenant_id = ? and email = ?
                        """, before.tenantId(), before.recipient());
            }
            audit(actorId,
                    passwordReset(before.templateCode())
                            ? AuditAction.PASSWORD_RESET_EMAIL_SENT
                            : merchantNotification(before.templateCode())
                            ? AuditAction.MERCHANT_NOTIFICATION_EMAIL_SENT
                            : temporaryCredentials(before.templateCode())
                            ? AuditAction.TEMPORARY_OWNER_CREDENTIALS_EMAIL_ACCEPTED_BY_RESEND
                            : AuditAction.ACTIVATION_EMAIL_ACCEPTED_BY_RESEND,
                    deliveryId,
                    before.tenantId(),
                    before.recipient(),
                    Map.of("provider", result.provider().name(), "providerMessageId", String.valueOf(result.providerMessageId())));
            log.info("email_event event=Email Sent delivery_id={} provider={} provider_message_id={} recipient={} template={} tenant_id={} attempt={} duration_ms={}",
                    deliveryId,
                    result.provider(),
                    result.providerMessageId(),
                    before.recipient(),
                    before.templateCode(),
                    before.tenantId(),
                    attempt,
                    durationMs);
            return;
        }
        boolean retryScheduled = result.retryable() && attempt < emailProperties.maxAttempts();
        Instant nextRetry = retryScheduled ? Instant.now().plusSeconds(emailProperties.retryDelaySeconds(attempt)) : null;
        jdbcTemplate.update("""
                update email_deliveries
                set status = ?, provider = ?, failed_at = now(), next_retry_at = ?, failure_code = ?,
                    failure_message_sanitized = ?, updated_at = now(), version = version + 1
                where id = ?
                """,
                retryScheduled ? EmailDeliveryStatus.RETRY_SCHEDULED.name() : EmailDeliveryStatus.FAILED.name(),
                result.provider().name(),
                timestamp(nextRetry),
                clean(result.failureCode(), 120),
                clean(result.failureMessage(), 1000),
                deliveryId);
        AuditAction failureAction = passwordReset(before.templateCode())
                ? AuditAction.PASSWORD_RESET_EMAIL_FAILED
                : merchantNotification(before.templateCode())
                ? AuditAction.MERCHANT_NOTIFICATION_EMAIL_FAILED
                : temporaryCredentials(before.templateCode()) ? AuditAction.TEMPORARY_CREDENTIALS_DELIVERY_FAILED
                : retryScheduled ? AuditAction.ACTIVATION_EMAIL_RETRY_SCHEDULED : AuditAction.ACTIVATION_EMAIL_FAILED;
        audit(actorId,
                failureAction,
                deliveryId,
                before.tenantId(),
                before.recipient(),
                Map.of("status", retryScheduled ? "RETRY_SCHEDULED" : "FAILED", "failureCode", String.valueOf(result.failureCode())));
        log.warn("email_event event={} delivery_id={} provider={} recipient={} template={} tenant_id={} attempt={} duration_ms={} failure_code={}",
                retryScheduled ? "Email Retried" : "Email Failed",
                deliveryId,
                result.provider(),
                before.recipient(),
                before.templateCode(),
                before.tenantId(),
                attempt,
                durationMs,
                result.failureCode());
    }

    private EmailMessage message(List<EmailRecipient> recipients, RenderedEmailTemplate rendered, EmailTemplateCode templateCode, Map<String, String> metadata) {
        return new EmailMessage(
                recipients,
                rendered.subject(),
                rendered.htmlBody(),
                rendered.textBody(),
                emailProperties.fromAddress(),
                emailProperties.fromName(),
                emailProperties.replyTo(),
                templateCode,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                metadata);
    }

    private UUID createDelivery(UUID tenantId, UUID invitationId, String recipient, EmailTemplateCode templateCode) {
        return createDelivery(tenantId, invitationId, recipient, templateCode, null, null, null);
    }

    private UUID createDelivery(
            UUID tenantId,
            UUID invitationId,
            String recipient,
            EmailTemplateCode templateCode,
            UUID actorId,
            String reason,
            String notes) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into email_deliveries (id, tenant_id, invitation_id, recipient, template_code, provider, status,
                                              correlation_id, requested_by_platform_user_id, requested_reason, requested_notes)
                values (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                """,
                id,
                tenantId,
                invitationId,
                normalizeRecipient(recipient),
                templateCode.name(),
                emailSender.provider().name(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                actorId,
                clean(reason, 1000),
                clean(notes, 2000));
        log.info("email_event event=Email Queued delivery_id={} provider={} recipient={} template={} tenant_id={}",
                id,
                emailSender.provider(),
                normalizeRecipient(recipient),
                templateCode,
                tenantId);
        return id;
    }

    private RowMapper<EmailDeliveryResponse> mapper() {
        return (rs, rowNum) -> new EmailDeliveryResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("invitation_id", UUID.class),
                rs.getString("recipient"),
                EmailTemplateCode.valueOf(rs.getString("template_code")),
                EmailProvider.valueOf(rs.getString("provider")),
                rs.getString("provider_message_id"),
                EmailDeliveryStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                instant(rs.getObject("last_attempt_at")),
                instant(rs.getObject("sent_at")),
                instant(rs.getObject("failed_at")),
                instant(rs.getObject("next_retry_at")),
                rs.getString("failure_code"),
                rs.getString("failure_message_sanitized"),
                rs.getString("correlation_id"),
                rs.getObject("requested_by_platform_user_id", UUID.class),
                rs.getString("requested_reason"),
                rs.getString("requested_notes"),
                instant(rs.getObject("created_at")),
                instant(rs.getObject("updated_at")),
                rs.getLong("version"));
    }

    private boolean successfulDeliveryExists(UUID invitationId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from email_deliveries
                where invitation_id = ? and status = 'SENT'
                """, Integer.class, invitationId);
        return count != null && count > 0;
    }

    private void audit(UUID actorId, AuditAction action, UUID deliveryId, UUID tenantId, String recipient, Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorId,
                action,
                "EMAIL_DELIVERY",
                deliveryId,
                null,
                null,
                null,
                after == null ? Map.of("tenantId", String.valueOf(tenantId), "recipient", recipient) : after,
                null));
    }

    private static String normalizeRecipient(String recipient) {
        if (recipient == null || recipient.isBlank() || !recipient.contains("@")) {
            throw new BadRequestException("recipient must be a valid email address");
        }
        return recipient.trim().toLowerCase();
    }

    private static boolean passwordReset(EmailTemplateCode templateCode) {
        return templateCode == EmailTemplateCode.PASSWORD_RESET;
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Unsupported timestamp value: " + value);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private static boolean merchantNotification(EmailTemplateCode templateCode) {
        return templateCode == EmailTemplateCode.MERCHANT_SUSPENDED
                || templateCode == EmailTemplateCode.MERCHANT_REACTIVATED;
    }

    private static boolean temporaryCredentials(EmailTemplateCode templateCode) {
        return templateCode == EmailTemplateCode.MERCHANT_OWNER_TEMPORARY_CREDENTIALS
                || templateCode == EmailTemplateCode.MERCHANT_OWNER_TEMPORARY_CREDENTIALS_RESEND;
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
