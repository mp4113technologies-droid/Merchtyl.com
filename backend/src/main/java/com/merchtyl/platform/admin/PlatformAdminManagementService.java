package com.merchtyl.platform.admin;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.auth.PasswordPolicyService;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.email.PlatformAdminInvitationEmailEvent;
import com.merchtyl.security.RoleName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static com.merchtyl.platform.admin.PlatformAdminDtos.*;

@Service
public class PlatformAdminManagementService {
    private static final Logger log = LoggerFactory.getLogger(PlatformAdminManagementService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PlatformUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicy;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public PlatformAdminManagementService(JdbcTemplate jdbc, PlatformUserRepository users, PasswordEncoder passwordEncoder,
                                          PasswordPolicyService passwordPolicy, ApplicationEventPublisher events, AuditService audit) {
        this.jdbc = jdbc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.events = events;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page list(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Long total = jdbc.queryForObject("select count(*) from platform_users", Long.class);
        var content = jdbc.query("""
                select target.id, target.first_name, target.last_name, target.email, target.role, target.status,
                       target.locked, target.last_login_at, target.created_at, target.version,
                       creator.id creator_id, creator.display_name creator_name
                from platform_users target left join platform_users creator on creator.id = target.created_by_platform_user_id
                order by target.created_at desc, target.id desc limit ? offset ?
                """, (rs, row) -> new Response(rs.getObject("id", UUID.class), rs.getString("first_name"),
                rs.getString("last_name"), rs.getString("email"), RoleName.valueOf(rs.getString("role")),
                rs.getString("status"), rs.getBoolean("locked"), instant(rs.getTimestamp("last_login_at")),
                instant(rs.getTimestamp("created_at")), rs.getObject("creator_id") == null ? null :
                new ActorSummary(rs.getObject("creator_id", UUID.class), rs.getString("creator_name")), rs.getLong("version")),
                safeSize, safePage * safeSize);
        long count = total == null ? 0 : total;
        return new Page(content, safePage, safeSize, count, (int) Math.ceil((double) count / safeSize));
    }

    @Transactional
    public Response create(CreateRequest request, Authentication authentication) {
        PlatformUserAccount actor = actor(authentication);
        requireRole(request.role());
        String email = request.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent() || Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from security_users where lower(email)=lower(?))", Boolean.class, email))) {
            throw new ConflictException("EMAIL_ALREADY_IN_USE");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into platform_users(id,email,display_name,first_name,last_name,password_hash,role,enabled,locked,
                  password_change_required,status,created_by_platform_user_id)
                values(?,?,?,?,?,null,?,false,false,true,'PENDING_ACTIVATION',?)
                """, id, email, request.firstName().trim() + " " + request.lastName().trim(), request.firstName().trim(),
                request.lastName().trim(), request.role().name(), actor.id());
        sendInvitation(id, actor, email, request.firstName().trim(), request.role());
        record(actor.id(), AuditAction.PLATFORM_ADMIN_CREATED, id, Map.of("role", request.role(), "status", "PENDING_ACTIVATION"));
        log.info("PLATFORM_ADMIN_CREATED actorUserId={} targetPublicId={} targetRole={}", actor.id(), id, request.role());
        return find(id);
    }

    @Transactional
    public Response resend(UUID id, Authentication authentication) {
        PlatformUserAccount actor = actor(authentication);
        Response target = find(id);
        if (!"PENDING_ACTIVATION".equals(target.status())) throw new ConflictException("INVITATION_ALREADY_ACCEPTED");
        jdbc.update("update platform_admin_invitations set status='REVOKED', revoked_at=now() where platform_user_id=? and status='PENDING'", id);
        sendInvitation(id, actor, target.email(), target.firstName(), target.role());
        return find(id);
    }

    @Transactional
    public void activate(ActivateRequest request) {
        passwordPolicy.validate(request.password());
        Map<String,Object> invite = jdbc.query("""
                select id, platform_user_id, purpose, status, expires_at from platform_admin_invitations where token_hash=? for update
                """, (rs, row) -> {
                    Map<String, Object> values = new java.util.HashMap<>();
                    values.put("id", rs.getObject("id", UUID.class));
                    values.put("userId", rs.getObject("platform_user_id", UUID.class));
                    values.put("purpose", rs.getString("purpose"));
                    values.put("status", rs.getString("status"));
                    values.put("expires", rs.getTimestamp("expires_at").toInstant());
                    return values;
                }, hash(request.token()))
                .stream().findFirst().orElseThrow(() -> new ConflictException("INVALID_ACTIVATION_TOKEN"));
        if (!"PLATFORM_ADMIN_ACTIVATION".equals(invite.get("purpose"))) throw new ConflictException("INVALID_ACTIVATION_TOKEN");
        if ("ACCEPTED".equals(invite.get("status"))) throw new ConflictException("ACTIVATION_TOKEN_ALREADY_USED");
        if ("REVOKED".equals(invite.get("status"))) throw new ConflictException("ACTIVATION_TOKEN_REVOKED");
        if (!((Instant) invite.get("expires")).isAfter(Instant.now())) {
            jdbc.update("update platform_admin_invitations set status='EXPIRED' where id=?", invite.get("id"));
            throw new ConflictException("EXPIRED_ACTIVATION_TOKEN");
        }
        UUID userId = (UUID) invite.get("userId");
        jdbc.update("update platform_users set password_hash=?,enabled=true,status='ACTIVE',password_change_required=false,updated_at=now(),version=version+1 where id=? and status='PENDING_ACTIVATION'",
                passwordEncoder.encode(request.password()), userId);
        jdbc.update("update platform_admin_invitations set status='ACCEPTED',accepted_at=now() where id=?", invite.get("id"));
        jdbc.update("update platform_admin_invitations set status='REVOKED',revoked_at=now() where platform_user_id=? and status='PENDING' and id<>?", userId, invite.get("id"));
        record(userId, AuditAction.PLATFORM_ADMIN_ACTIVATED, userId, Map.of("status", "ACTIVE"));
        log.info("PLATFORM_ADMIN_ACTIVATION_COMPLETED targetPublicId={}", userId);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Response status(UUID id, StatusRequest request, Authentication authentication) {
        PlatformUserAccount actor = actor(authentication);
        if (actor.id().equals(id)) throw new ConflictException("PLATFORM_ADMIN_SELF_MODIFICATION_NOT_ALLOWED");
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtext('platform-super-admin-lifecycle'))", Long.class);
        Response before = find(id);
        boolean enabling = request.enabled();
        if (!enabling && before.role() == RoleName.PLATFORM_SUPER_ADMIN) {
            Long count = jdbc.queryForObject("select count(*) from platform_users where role='PLATFORM_SUPER_ADMIN' and enabled=true", Long.class);
            if (count == null || count <= 1) throw new ConflictException("LAST_SUPER_ADMIN_REQUIRED");
        }
        int updated = jdbc.update("update platform_users set enabled=?,status=?,updated_at=now(),version=version+1 where id=? and version=?",
                enabling, enabling ? "ACTIVE" : "DEACTIVATED", id, request.version());
        if (updated != 1) throw new ConflictException("Platform administrator was modified by another request");
        record(actor.id(), enabling ? AuditAction.PLATFORM_ADMIN_REACTIVATED : AuditAction.PLATFORM_ADMIN_DEACTIVATED,
                id, Map.of("previousStatus", before.status(), "newStatus", enabling ? "ACTIVE" : "DEACTIVATED", "role", before.role()));
        log.info("PLATFORM_ADMIN_STATUS_CHANGED actorUserId={} targetPublicId={} targetRole={} enabled={}", actor.id(), id, before.role(), enabling);
        return find(id);
    }

    private void sendInvitation(UUID id, PlatformUserAccount actor, String email, String firstName, RoleName role) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = Instant.now().plus(48, ChronoUnit.HOURS);
        jdbc.update("insert into platform_admin_invitations(id,platform_user_id,token_hash,purpose,status,expires_at,created_by_platform_user_id) values(?,?,?,'PLATFORM_ADMIN_ACTIVATION','PENDING',?,?)",
                UUID.randomUUID(), id, hash(token), Timestamp.from(expires), actor.id());
        events.publishEvent(new PlatformAdminInvitationEmailEvent(actor.id(), id, email, firstName, role, token, expires));
        record(actor.id(), AuditAction.PLATFORM_ADMIN_INVITATION_SENT, id, Map.of("role", role, "expiresAt", expires));
    }

    private Response find(UUID id) {
        return jdbc.query("""
                select target.id,target.first_name,target.last_name,target.email,target.role,target.status,target.locked,
                  target.last_login_at,target.created_at,target.version,creator.id creator_id,creator.display_name creator_name
                from platform_users target left join platform_users creator on creator.id=target.created_by_platform_user_id where target.id=?
                """, (rs,row) -> new Response(rs.getObject("id",UUID.class),rs.getString("first_name"),rs.getString("last_name"),
                rs.getString("email"),RoleName.valueOf(rs.getString("role")),rs.getString("status"),rs.getBoolean("locked"),
                instant(rs.getTimestamp("last_login_at")),instant(rs.getTimestamp("created_at")),rs.getObject("creator_id")==null?null:
                new ActorSummary(rs.getObject("creator_id",UUID.class),rs.getString("creator_name")),rs.getLong("version")),id)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("PLATFORM_ADMIN_NOT_FOUND"));
    }

    private PlatformUserAccount actor(Authentication authentication) {
        return users.findByEmail(authentication.getName()).orElseThrow(() -> new ConflictException("PLATFORM_ADMIN_ACCESS_DENIED"));
    }
    private static void requireRole(RoleName role) {
        if (role != RoleName.PLATFORM_SUPER_ADMIN && role != RoleName.PLATFORM_SUPPORT_ADMIN) throw new ConflictException("INVALID_PLATFORM_ADMIN_ROLE");
    }
    private void record(UUID actor, AuditAction action, UUID target, Object after) {
        audit.record(new CreateAuditRecordCommand(actor, action, "PLATFORM_ADMIN", target, null, null, null, after, null));
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
