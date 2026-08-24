package com.merchtyl.auth;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.config.JwtProperties;
import com.merchtyl.config.SecurityProperties;
import com.merchtyl.platform.admin.PlatformAdministrationService;
import com.merchtyl.platform.admin.TenantStatus;
import com.merchtyl.platform.web.CorrelationIdFilter;
import com.merchtyl.security.CreateSecurityUserCommand;
import com.merchtyl.security.RefreshToken;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.security.PermissionCode;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.SecurityUserService;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRoleRepository;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    @org.springframework.beans.factory.annotation.Autowired
    private PasswordPolicyService passwordPolicyService;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final SecurityUserService securityUserService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;
    private final PlatformAdministrationService platformAdministrationService;
    private final JdbcTemplate jdbcTemplate;
    private final SecurityProperties securityProperties;
    private final LoginAttemptService loginAttemptService;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            SecurityUserService securityUserService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            JwtProperties jwtProperties,
            AuditService auditService,
            PlatformAdministrationService platformAdministrationService,
            JdbcTemplate jdbcTemplate,
            SecurityProperties securityProperties,
            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.securityUserService = securityUserService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
        this.auditService = auditService;
        this.platformAdministrationService = platformAdministrationService;
        this.jdbcTemplate = jdbcTemplate;
        this.securityProperties = securityProperties;
        this.loginAttemptService = loginAttemptService;
    }

    AuthService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            SecurityUserService securityUserService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            JwtProperties jwtProperties,
            AuditService auditService) {
        this(userRepository, userRoleRepository, securityUserService, passwordEncoder, jwtService, refreshTokenService,
                jwtProperties, auditService, null, null, new SecurityProperties(null, null, null), null);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        if (userRepository.count() > 0) {
            throw new ForbiddenOperationException("Self-registration is closed after owner bootstrap");
        }

        User saved = securityUserService.createUser(new CreateSecurityUserCommand(
                email,
                request.displayName().trim(),
                request.password(),
                RoleName.OWNER));
        return issueTokenResponse(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            auditLoginFailure(null, email, "unknown_user");
            log.warn("authentication_event event=Failed Login email={} reason=unknown_user", email);
            throw badCredentials();
        }
        if (user.isLocked()) {
            log.warn("security_event event=ACCOUNT_LOCKED user_id={} tenant_id={}", user.getId(), user.getTenantId());
            throw new AccountLockedException();
        }
        try {
            rejectIfCannotAuthenticate(user);
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                boolean locked = loginAttemptService != null && loginAttemptService.recordFailure(user.getId());
                if (locked) {
                    throw new AccountLockedException();
                }
                throw badCredentials();
            }
        } catch (BadCredentialsException exception) {
            if (user != null && user.isPasswordChangeRequired()) {
                auditService.record(new CreateAuditRecordCommand(
                        user.getId(),
                        AuditAction.FIRST_TEMPORARY_PASSWORD_LOGIN_FAILED,
                        "USER",
                        user.getId(),
                        null,
                        null,
                        null,
                        Map.of("email", user.getEmail(), "status", "failure"),
                        "bad_credentials"));
            }
            auditLoginFailure(user, email, "bad_credentials");
            log.warn("authentication_event event=Failed Login email={} user_id={} tenant_id={} reason=bad_credentials",
                    email,
                    user.getId(),
                    user.getTenantId());
            throw exception;
        }

        if (loginAttemptService != null) {
            loginAttemptService.recordSuccess(user.getId());
        }

        if (user.isPasswordChangeRequired()) {
            AuthResponse response = passwordChangeRequiredResponse(user, request.password());
            auditService.record(new CreateAuditRecordCommand(
                    user.getId(),
                    AuditAction.FIRST_TEMPORARY_PASSWORD_LOGIN_SUCCEEDED,
                    "USER",
                    user.getId(),
                    null,
                    null,
                    null,
                    Map.of("email", user.getEmail(), "status", "password_change_required"),
                    null));
            log.info("authentication_event event=First Login Completed status=password_change_required user_id={} tenant_id={} username={}",
                    user.getId(),
                    user.getTenantId(),
                    user.getEmail());
            return response;
        }

        AuthResponse response = issueTokenResponse(user);
        auditService.record(new CreateAuditRecordCommand(
                user.getId(),
                AuditAction.LOGIN_SUCCESS,
                "USER",
                user.getId(),
                null,
                null,
                null,
                Map.of(
                        "email", user.getEmail(),
                        "roles", roles(user),
                        "status", "success"),
                null));
        log.info("authentication_event event=Successful Login user_id={} tenant_id={} username={}",
                user.getId(),
                user.getTenantId(),
                user.getEmail());
        return response;
    }

    @Transactional
    public void firstLoginChangePassword(FirstLoginPasswordChangeRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("newPassword and confirmPassword must match");
        }
        if (passwordPolicyService == null) new PasswordPolicyService().validate(request.newPassword());
        else passwordPolicyService.validate(request.newPassword());
        Map<String, Object> token = findActivePasswordChangeToken(request.passwordChangeToken());
        UUID userId = (UUID) token.get("user_id");
        User user = userRepository.findById(userId).orElseThrow(AuthService::badCredentials);
        rejectIfCannotAuthenticate(user);
        if (!user.isPasswordChangeRequired()) {
            rejectPasswordChangeToken(userId, "password_change_not_required");
        }
        Instant now = Instant.now();
        if (user.getTemporaryPasswordExpiresAt() == null || !user.getTemporaryPasswordExpiresAt().isAfter(now)) {
            rejectPasswordChangeToken(userId, "temporary_credentials_expired");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("newPassword must be different from the temporary password");
        }
        jdbcTemplate.update("""
                update first_login_password_change_tokens
                set used_at = now(), updated_at = now(), version = version + 1
                where id = ?
                """, token.get("id"));
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        if (platformAdministrationService != null && user.getTenantId() != null) {
            platformAdministrationService.markOwnerPasswordChanged(user.getTenantId(), user.getId());
        }
        auditService.record(new CreateAuditRecordCommand(
                user.getId(),
                AuditAction.MANDATORY_PASSWORD_CHANGE_COMPLETED,
                "USER",
                user.getId(),
                null,
                null,
                null,
                Map.of("tenantId", String.valueOf(user.getTenantId()), "status", "completed"),
                null));
        log.info("authentication_event event=Password Changed user_id={} tenant_id={} username={}",
                user.getId(),
                user.getTenantId(),
                user.getEmail());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        Instant now = Instant.now();
        RefreshToken presentedToken = refreshTokenService.findByRawToken(request.refreshToken())
                .orElseThrow(AuthService::badCredentials);

        if (!presentedToken.isActive(now)) {
            if (presentedToken.getRevokedAt() != null) {
                refreshTokenService.revokeActiveTokensForUser(presentedToken.getUser(), now);
            }
            throw badCredentials();
        }

        User user = presentedToken.getUser();
        rejectIfCannotAuthenticate(user);

        String rawRefreshToken = refreshTokenService.generateRawToken();
        Instant refreshExpiresAt = refreshExpiresAt(now);
        RefreshToken replacement = refreshTokenService.createRefreshToken(user, rawRefreshToken, refreshExpiresAt);
        presentedToken.revoke(now, replacement.getId());

        return response(user, rawRefreshToken, refreshExpiresAt, now);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        Instant now = Instant.now();
        refreshTokenService.findByRawToken(request.refreshToken())
                .filter(token -> token.isActive(now))
                .ifPresent(token -> {
                    token.revoke(now, null);
                    User user = token.getUser();
                    auditService.record(new CreateAuditRecordCommand(
                            user.getId(),
                            AuditAction.LOGOUT,
                            "USER",
                            user.getId(),
                            null,
                            null,
                            null,
                            Map.of(
                                    "email", user.getEmail(),
                                    "status", "logged_out"),
                            null));
                    log.info("authentication_event event=Logout user_id={} tenant_id={} username={}",
                            user.getId(),
                            user.getTenantId(),
                            user.getEmail());
                });
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw badCredentials();
        }
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(AuthService::badCredentials);
        rejectIfCannotAuthenticate(user);
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                roles(user),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> {
                            try {
                                PermissionCode.valueOf(authority);
                                return true;
                            } catch (IllegalArgumentException ignored) {
                                return false;
                            }
                        })
                        .sorted()
                        .toList());
    }

    private AuthResponse issueTokenResponse(User user) {
        Instant now = Instant.now();
        String rawRefreshToken = refreshTokenService.generateRawToken();
        Instant refreshExpiresAt = refreshExpiresAt(now);
        refreshTokenService.createRefreshToken(user, rawRefreshToken, refreshExpiresAt);
        return response(user, rawRefreshToken, refreshExpiresAt, now);
    }

    private AuthResponse response(User user, String rawRefreshToken, Instant refreshExpiresAt, Instant now) {
        Instant accessExpiresAt = now.plusSeconds(jwtProperties.expirationMinutes() * 60);
        List<RoleName> roles = roles(user);
        return new AuthResponse(
                "AUTHENTICATED",
                jwtService.issueAccessToken(user, roles, now, accessExpiresAt),
                rawRefreshToken,
                "Bearer",
                accessExpiresAt,
                refreshExpiresAt,
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                roles,
                null,
                null,
                null);
    }

    private AuthResponse passwordChangeRequiredResponse(User user, String rawTemporaryPassword) {
        Instant now = Instant.now();
        if (user.getTemporaryPasswordExpiresAt() == null || !user.getTemporaryPasswordExpiresAt().isAfter(now)) {
            auditService.record(new CreateAuditRecordCommand(
                    user.getId(),
                    AuditAction.TEMPORARY_CREDENTIALS_EXPIRED,
                    "USER",
                    user.getId(),
                    null,
                    null,
                    null,
                    Map.of("email", user.getEmail(), "status", "expired"),
                    null));
            log.warn("authentication_event event=Failed Login user_id={} tenant_id={} reason=temporary_credentials_expired",
                    user.getId(),
                    user.getTenantId());
            throw new BadCredentialsException("Temporary credentials have expired");
        }
        user.markFirstLogin(now);
        userRepository.save(user);
        revokeActivePasswordChangeTokens(user.getId(), now);
        String rawToken = generateRawToken();
        Instant expiresAt = now.plus(securityProperties.temporaryPassword().passwordChangeTokenTtl());
        jdbcTemplate.update("""
                insert into first_login_password_change_tokens
                    (id, user_id, tenant_id, token_hash, purpose, expires_at, correlation_id)
                values (?, ?, ?, ?, 'FIRST_LOGIN_PASSWORD_CHANGE', ?, ?)
                """,
                UUID.randomUUID(),
                user.getId(),
                user.getTenantId(),
                hashToken(rawToken),
                timestamp(expiresAt),
                MDC.get(CorrelationIdFilter.MDC_KEY));
        return new AuthResponse(
                "PASSWORD_CHANGE_REQUIRED",
                null,
                null,
                "PasswordChange",
                null,
                null,
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                roles(user),
                rawToken,
                expiresAt,
                securityProperties.temporaryPassword().passwordChangeTokenTtl().toSeconds());
    }

    private Map<String, Object> findActivePasswordChangeToken(String rawToken) {
        return jdbcTemplate.queryForList("""
                select *
                from first_login_password_change_tokens
                where token_hash = ? and purpose = 'FIRST_LOGIN_PASSWORD_CHANGE'
                  and used_at is null and revoked_at is null and expires_at > now()
                """, hashToken(rawToken)).stream().findFirst()
                .orElseThrow(() -> {
                    auditService.record(new CreateAuditRecordCommand(
                            null,
                            AuditAction.PASSWORD_CHANGE_TOKEN_REJECTED,
                            "FIRST_LOGIN_PASSWORD_CHANGE_TOKEN",
                            null,
                            null,
                            null,
                            null,
                            Map.of("status", "rejected"),
                            "invalid_or_expired"));
                    return badCredentials();
                });
    }

    public void revokeActivePasswordChangeTokens(UUID userId, Instant revokedAt) {
        if (jdbcTemplate == null || userId == null) {
            return;
        }
        jdbcTemplate.update("""
                update first_login_password_change_tokens
                set revoked_at = ?, updated_at = now(), version = version + 1
                where user_id = ? and used_at is null and revoked_at is null
                """, timestamp(revokedAt == null ? Instant.now() : revokedAt), userId);
    }

    private void rejectPasswordChangeToken(UUID userId, String reason) {
        auditService.record(new CreateAuditRecordCommand(
                userId,
                AuditAction.PASSWORD_CHANGE_TOKEN_REJECTED,
                "USER",
                userId,
                null,
                null,
                null,
                Map.of("status", "rejected"),
                reason));
        throw badCredentials();
    }

    private Instant refreshExpiresAt(Instant now) {
        return now.plusSeconds(jwtProperties.refreshExpirationDays() * 24 * 60 * 60);
    }

    private List<RoleName> roles(User user) {
        return userRoleRepository.findByUser(user).stream()
                .map(userRole -> userRole.getRole().getName())
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    private void auditLoginFailure(User user, String email, String reason) {
        auditService.record(new CreateAuditRecordCommand(
                user == null ? null : user.getId(),
                AuditAction.LOGIN_FAILURE,
                "USER",
                user == null ? null : user.getId(),
                null,
                null,
                null,
                Map.of(
                        "email", email,
                        "status", "failure"),
                reason));
    }

    private void rejectIfCannotAuthenticate(User user) {
        if (!user.isEnabled() || user.isLocked()) {
            throw badCredentials();
        }
        if (platformAdministrationService == null) {
            return;
        }
        platformAdministrationService.tenantStatus(user.getTenantId()).ifPresent(status -> {
            if (status == TenantStatus.SUSPENDED || status == TenantStatus.CLOSED || status == TenantStatus.REJECTED) {
                throw badCredentials();
            }
        });
    }

    private static BadCredentialsException badCredentials() {
        return new BadCredentialsException("Invalid credentials");
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

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
