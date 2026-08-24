package com.merchtyl.auth;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.TooManyRequestsException;
import com.merchtyl.config.SecurityProperties;
import com.merchtyl.email.EmailDeliveryResponse;
import com.merchtyl.email.EmailDeliveryService;
import com.merchtyl.platform.admin.PlatformUserRepository;
import com.merchtyl.platform.web.CorrelationIdFilter;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PasswordResetService {
    public static final String GENERIC_MESSAGE = "If an eligible account exists, password reset instructions will be sent.";
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailDeliveryService emailDeliveryService;
    private final AuditService auditService;
    private final PlatformUserRepository platformUserRepository;
    private final SecurityProperties properties;
    private final PasswordPolicyService passwordPolicyService;

    public PasswordResetService(JdbcTemplate jdbcTemplate, UserRepository userRepository, PasswordEncoder passwordEncoder,
                                RefreshTokenService refreshTokenService, EmailDeliveryService emailDeliveryService,
                                AuditService auditService, PlatformUserRepository platformUserRepository,
                                SecurityProperties properties, PasswordPolicyService passwordPolicyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailDeliveryService = emailDeliveryService;
        this.auditService = auditService;
        this.platformUserRepository = platformUserRepository;
        this.properties = properties;
        this.passwordPolicyService = passwordPolicyService;
    }

    @Transactional
    public PasswordResetMessage forgotPassword(ForgotPasswordRequest request, String requestIp) {
        userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .filter(this::eligible)
                .filter(user -> !closedTenant(user.getTenantId()))
                .ifPresent(user -> {
                    if (!withinLimit(user.getId(), properties.passwordReset().forgotMaxPerHour())) {
                        log.warn("security_event event=PASSWORD_RESET_REQUESTED status=rate_limited user_id={} tenant_id={}", user.getId(), user.getTenantId());
                        return;
                    }
                    createAndSend(user, "SELF_SERVICE", null, null, requestIp);
                });
        return new PasswordResetMessage(GENERIC_MESSAGE);
    }

    @Transactional
    public EmailDeliveryResponse adminSend(UUID tenantId, UUID userId, AdminPasswordResetRequest request, Authentication authentication) {
        UUID actorId = platformActor(authentication);
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new NotFoundException("Merchant user not found"));
        if (!eligible(user)) {
            throw new ForbiddenOperationException("Target account is not eligible for password reset");
        }
        if (closedTenant(tenantId)) {
            throw new ForbiddenOperationException("Closed merchant accounts cannot reset passwords");
        }
        if (!withinLimit(userId, properties.passwordReset().adminMaxPerHour())) {
            throw new TooManyRequestsException("Password reset send rate limit exceeded", Duration.ofHours(1));
        }
        return createAndSend(user, "PLATFORM_ADMIN", actorId, request.reason(), null);
    }

    @Transactional
    public void reset(ResetPasswordRequest request) {
        log.info("security_event event=PASSWORD_RESET_VALIDATION_STARTED token_purpose=PASSWORD_RESET");
        if (!request.newPassword().equals(request.confirmPassword())) {
            log.warn("security_event event=PASSWORD_RESET_PASSWORD_REJECTED failure_code=PASSWORD_CONFIRMATION_MISMATCH");
            throw new PasswordConfirmationException();
        }
        try {
            passwordPolicyService.validate(request.newPassword());
        } catch (PasswordPolicyException exception) {
            log.warn("security_event event=PASSWORD_RESET_PASSWORD_REJECTED failure_code={}", exception.violations().getFirst().code());
            throw exception;
        }
        Map<String, Object> token = jdbcTemplate.queryForList("""
                select * from password_reset_tokens where token_hash = ?
                """, hash(request.token())).stream().findFirst()
                .orElseThrow(() -> rejected("INVALID_RESET_TOKEN", "Invalid password reset link"));
        if (!"PASSWORD_RESET".equals(token.get("purpose"))) {
            throw rejected("RESET_TOKEN_PURPOSE_INVALID", "Invalid password reset link");
        }
        UUID userId = (UUID) token.get("user_id");
        Instant expiresAt = instant(token.get("expires_at"));
        if (token.get("used_at") != null) throw rejected("RESET_TOKEN_ALREADY_USED", "Password reset link has already been used");
        if (token.get("revoked_at") != null) throw rejected("RESET_TOKEN_REVOKED", "Password reset link has been revoked");
        if (!expiresAt.isAfter(Instant.now())) {
            audit(userId, AuditAction.PASSWORD_RESET_TOKEN_EXPIRED, "expired", null);
            throw rejected("EXPIRED_RESET_TOKEN", "Password reset link has expired");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> rejected("INVALID_RESET_TOKEN", "Invalid password reset link"));
        if (!user.isEnabled()) throw restriction("ACCOUNT_DISABLED", "Password reset is not allowed for this account");
        if (!eligible(user)) throw restriction("PASSWORD_RESET_NOT_ALLOWED", "Password reset is not allowed for this account");
        String tenantStatus = jdbcTemplate.queryForObject("select status from tenants where id = ?", String.class, user.getTenantId());
        if ("CLOSED".equals(tenantStatus)) throw restriction("TENANT_CLOSED", "Password reset is not allowed for this merchant");
        if ("SUSPENDED".equals(tenantStatus)) throw restriction("TENANT_SUSPENDED", "Password reset is not allowed while the merchant is suspended");
        log.info("security_event event=PASSWORD_RESET_TOKEN_VALIDATED user_id={} tenant_id={} token_purpose=PASSWORD_RESET", userId, user.getTenantId());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            log.warn("security_event event=PASSWORD_RESET_PASSWORD_REJECTED user_id={} tenant_id={} failure_code=PASSWORD_MATCHES_CURRENT_PASSWORD", userId, user.getTenantId());
            throw new PasswordPolicyException(List.of(new PasswordPolicyViolation("PASSWORD_MATCHES_CURRENT_PASSWORD", "Choose a password different from your current password.")));
        }
        int used = jdbcTemplate.update("""
                update password_reset_tokens set used_at = now(), version = version + 1
                where id = ? and used_at is null and revoked_at is null and expires_at > now()
                """, token.get("id"));
        if (used != 1) {
            throw rejected("INVALID_RESET_TOKEN", "Invalid password reset link");
        }
        Instant now = Instant.now();
        user.completePasswordReset(passwordEncoder.encode(request.newPassword()), now);
        userRepository.save(user);
        revokeOthers(userId, (UUID) token.get("id"));
        refreshTokenService.revokeActiveTokensForUser(user, now);
        jdbcTemplate.update("""
                update first_login_password_change_tokens set revoked_at = now(), updated_at = now(), version = version + 1
                where user_id = ? and used_at is null and revoked_at is null
                """, userId);
        audit(userId, AuditAction.PASSWORD_RESET_COMPLETED, "completed", null);
        log.info("security_event event=PASSWORD_RESET_COMPLETED user_id={} tenant_id={}", userId, user.getTenantId());
    }

    @Transactional
    public void unlock(UUID tenantId, UUID userId, AdminPasswordResetRequest request, Authentication authentication) {
        UUID actorId = platformActor(authentication);
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new NotFoundException("Merchant user not found"));
        user.unlock();
        userRepository.save(user);
        auditService.record(new CreateAuditRecordCommand(actorId, AuditAction.ACCOUNT_UNLOCKED_BY_ADMIN,
                "USER", userId, null, null, null, Map.of("tenantId", tenantId, "targetUserId", userId), request.reason()));
        log.info("security_event event=ACCOUNT_UNLOCKED actor_user_id={} user_id={} tenant_id={}", actorId, userId, tenantId);
    }

    private EmailDeliveryResponse createAndSend(User user, String creatorType, UUID actorId, String reason, String requestIp) {
        Instant now = Instant.now();
        int revoked = jdbcTemplate.update("""
                update password_reset_tokens set revoked_at = now(), version = version + 1
                where user_id = ? and used_at is null and revoked_at is null
                """, user.getId());
        if (revoked > 0) {
            audit(user.getId(), AuditAction.PASSWORD_RESET_TOKEN_REVOKED, "superseded", actorId);
        }
        String rawToken = rawToken();
        Instant expiresAt = now.plus(properties.passwordReset().tokenTtl());
        UUID tokenId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into password_reset_tokens
                    (id, public_id, user_id, tenant_id, token_hash, expires_at, created_by_type,
                     created_by_user_id, request_ip, correlation_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, tokenId, UUID.randomUUID(), user.getId(), user.getTenantId(), hash(rawToken), Timestamp.from(expiresAt),
                creatorType, actorId, requestIp, MDC.get(CorrelationIdFilter.MDC_KEY));
        auditService.record(new CreateAuditRecordCommand(actorId == null ? user.getId() : actorId,
                actorId == null ? AuditAction.PASSWORD_RESET_REQUESTED : AuditAction.PASSWORD_RESET_REQUESTED_BY_ADMIN,
                "PASSWORD_RESET_TOKEN", tokenId, null, null, null,
                Map.of("tenantId", user.getTenantId(), "targetUserId", user.getId(), "expiresAt", expiresAt), reason));
        log.info("security_event event=PASSWORD_RESET_REQUESTED user_id={} tenant_id={} actor_user_id={}", user.getId(), user.getTenantId(), actorId);
        return emailDeliveryService.sendPasswordReset(user.getTenantId(), user.getEmail(), user.getDisplayName(), rawToken,
                expiresAt, actorId, reason);
    }

    private boolean withinLimit(UUID userId, int max) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from password_reset_tokens where user_id = ? and created_at >= now() - interval '1 hour'
                """, Integer.class, userId);
        return count == null || count < max;
    }

    private boolean eligible(User user) {
        if (!user.isEnabled() || user.getTenantId() == null) return false;
        List<String> roles = jdbcTemplate.queryForList("""
                select roles.name from security_roles roles join security_user_roles ur on ur.role_id = roles.id where ur.user_id = ?
                """, String.class, user.getId());
        return roles.stream().anyMatch(List.of("TENANT_OWNER", "STORE_MANAGER", "CASHIER")::contains);
    }

    private boolean closedTenant(UUID tenantId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select status = 'CLOSED' from tenants where id = ?", Boolean.class, tenantId));
    }

    private UUID platformActor(Authentication authentication) {
        if (authentication == null) throw new ForbiddenOperationException("Platform authentication is required");
        return platformUserRepository.findByEmail(authentication.getName()).orElseThrow(() -> new ForbiddenOperationException("Platform actor not found")).id();
    }

    private void revokeOthers(UUID userId, UUID usedTokenId) {
        jdbcTemplate.update("""
                update password_reset_tokens set revoked_at = now(), version = version + 1
                where user_id = ? and id <> ? and used_at is null and revoked_at is null
                """, userId, usedTokenId);
    }

    private ResetTokenException rejected(String code, String message) {
        log.warn("security_event event=PASSWORD_RESET_TOKEN_REJECTED failure_code={} token_purpose=PASSWORD_RESET", code);
        return new ResetTokenException(code, message);
    }

    private PasswordResetRestrictionException restriction(String code, String message) {
        log.warn("security_event event=PASSWORD_RESET_FAILED failure_code={} token_purpose=PASSWORD_RESET", code);
        return new PasswordResetRestrictionException(code, message);
    }

    private void audit(UUID userId, AuditAction action, String status, UUID actorId) {
        auditService.record(new CreateAuditRecordCommand(actorId == null ? userId : actorId, action, "USER", userId,
                null, null, null, Map.of("status", status), null));
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String rawToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Instant instant(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toInstant() : (Instant) value;
    }

}
