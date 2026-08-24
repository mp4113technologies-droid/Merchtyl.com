package com.merchtyl.auth;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.config.SecurityProperties;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final SecurityProperties properties;

    public LoginAttemptService(JdbcTemplate jdbcTemplate, UserRepository userRepository,
                               RefreshTokenService refreshTokenService, AuditService auditService,
                               SecurityProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(UUID userId) {
        int threshold = properties.login().maxFailedAttempts();
        var states = jdbcTemplate.queryForList("""
                update security_users
                set failed_login_attempts = least(failed_login_attempts + 1, ?),
                    last_failed_login_at = now(),
                    locked = failed_login_attempts + 1 >= ?,
                    locked_at = case when failed_login_attempts + 1 >= ? then coalesce(locked_at, now()) else locked_at end,
                    lock_reason = case when failed_login_attempts + 1 >= ? then 'FAILED_LOGIN_ATTEMPTS' else lock_reason end,
                    updated_at = now(), version = version + 1
                where id = ? and locked = false
                returning failed_login_attempts, locked, tenant_id
                """, threshold, threshold, threshold, threshold, userId);
        if (states.isEmpty()) {
            return true;
        }
        Map<String, Object> state = states.getFirst();
        boolean locked = Boolean.TRUE.equals(state.get("locked"));
        auditService.record(new CreateAuditRecordCommand(userId,
                locked ? AuditAction.ACCOUNT_LOCKED : AuditAction.FAILED_LOGIN_RECORDED,
                "USER", userId, null, null, null,
                Map.of("failedLoginAttempts", state.get("failed_login_attempts"), "locked", locked), null));
        if (locked) {
            userRepository.findById(userId).ifPresent(user -> refreshTokenService.revokeActiveTokensForUser(user, Instant.now()));
            log.warn("security_event event=ACCOUNT_LOCKED user_id={} tenant_id={} reason=FAILED_LOGIN_ATTEMPTS", userId, state.get("tenant_id"));
        } else {
            log.warn("security_event event=LOGIN_FAILED user_id={} tenant_id={}", userId, state.get("tenant_id"));
        }
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        jdbcTemplate.update("""
                update security_users set failed_login_attempts = 0, updated_at = now(), version = version + 1
                where id = ? and failed_login_attempts <> 0 and locked = false
                """, userId);
    }
}
