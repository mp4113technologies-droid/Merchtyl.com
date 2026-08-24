package com.merchtyl.platform.admin;

import com.merchtyl.security.RoleName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PlatformUserRepository {
    private static final RowMapper<PlatformUserAccount> MAPPER = PlatformUserRepository::map;

    private final JdbcTemplate jdbcTemplate;

    public PlatformUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PlatformUserAccount> findByEmail(String email) {
        List<PlatformUserAccount> users = jdbcTemplate.query("""
                select id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                       test_provisioned, test_provisioning_reference, test_provisioned_at,
                       created_at, updated_at, version
                from platform_users
                where lower(email) = lower(?)
                """, MAPPER, email);
        return users.stream().findFirst();
    }

    public Optional<PlatformUserAccount> findById(UUID id) {
        List<PlatformUserAccount> users = jdbcTemplate.query("""
                select id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                       test_provisioned, test_provisioning_reference, test_provisioned_at,
                       created_at, updated_at, version
                from platform_users
                where id = ?
                """, MAPPER, id);
        return users.stream().findFirst();
    }

    public List<PlatformUserAccount> findAll() {
        return jdbcTemplate.query("""
                select id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                       test_provisioned, test_provisioning_reference, test_provisioned_at,
                       created_at, updated_at, version
                from platform_users
                order by email, id
                """, MAPPER);
    }

    public List<PlatformUserAccount> findTestProvisioned() {
        return jdbcTemplate.query("""
                select id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                       test_provisioned, test_provisioning_reference, test_provisioned_at,
                       created_at, updated_at, version
                from platform_users
                where test_provisioned = true
                order by email, id
                """, MAPPER);
    }

    public List<PlatformUserAccount> findTestProvisionedByEmailContaining(String emailPattern) {
        return jdbcTemplate.query("""
                select id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                       test_provisioned, test_provisioning_reference, test_provisioned_at,
                       created_at, updated_at, version
                from platform_users
                where test_provisioned = true
                  and lower(email) like '%' || lower(?) || '%'
                order by email, id
                """, MAPPER, emailPattern);
    }

    public boolean existsSuperAdmin() {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1 from platform_users
                    where role = 'PLATFORM_SUPER_ADMIN' and enabled = true
                )
                """, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public long activeSuperAdminCount() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*) from platform_users
                where role = 'PLATFORM_SUPER_ADMIN' and enabled = true
                """, Long.class);
        return count == null ? 0 : count;
    }

    public PlatformUserAccount create(String email, String displayName, String passwordHash, RoleName role, boolean enabled, boolean passwordChangeRequired) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into platform_users (id, email, display_name, password_hash, role, enabled, locked, password_change_required)
                values (?, ?, ?, ?, ?, ?, false, ?)
                returning id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                          test_provisioned, test_provisioning_reference, test_provisioned_at,
                          created_at, updated_at, version
                """, MAPPER, id, email, displayName, passwordHash, role.name(), enabled, passwordChangeRequired);
    }

    public PlatformUserAccount createTestUser(
            String email,
            String displayName,
            String passwordHash,
            RoleName role,
            boolean enabled,
            boolean locked,
            boolean passwordChangeRequired,
            String reference) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into platform_users (
                    id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                    test_provisioned, test_provisioning_reference, test_provisioned_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, true, ?, now())
                returning id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                          test_provisioned, test_provisioning_reference, test_provisioned_at,
                          created_at, updated_at, version
                """, MAPPER, id, email, displayName, passwordHash, role.name(), enabled, locked, passwordChangeRequired, reference);
    }

    public PlatformUserAccount resetTestUser(
            UUID id,
            String displayName,
            String passwordHash,
            RoleName role,
            boolean enabled,
            boolean locked,
            boolean passwordChangeRequired,
            long version) {
        return jdbcTemplate.queryForObject("""
                update platform_users
                set display_name = ?, password_hash = ?, role = ?, enabled = ?, locked = ?,
                    password_change_required = ?, updated_at = now(), version = version + 1
                where id = ? and version = ? and test_provisioned = true
                returning id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                          test_provisioned, test_provisioning_reference, test_provisioned_at,
                          created_at, updated_at, version
                """, MAPPER, displayName, passwordHash, role.name(), enabled, locked, passwordChangeRequired, id, version);
    }

    public PlatformUserAccount update(UUID id, String email, String displayName, RoleName role, boolean locked, long version) {
        return jdbcTemplate.queryForObject("""
                update platform_users
                set email = ?, display_name = ?, role = ?, locked = ?, updated_at = now(), version = version + 1
                where id = ? and version = ?
                returning id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                          test_provisioned, test_provisioning_reference, test_provisioned_at,
                          created_at, updated_at, version
                """, MAPPER, email, displayName, role.name(), locked, id, version);
    }

    public PlatformUserAccount updateStatus(UUID id, boolean enabled, long version) {
        return jdbcTemplate.queryForObject("""
                update platform_users
                set enabled = ?, updated_at = now(), version = version + 1
                where id = ? and version = ?
                returning id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                          test_provisioned, test_provisioning_reference, test_provisioned_at,
                          created_at, updated_at, version
                """, MAPPER, enabled, id, version);
    }

    public PlatformUserAccount disableTestUser(UUID id, long version) {
        return jdbcTemplate.queryForObject("""
                update platform_users
                set enabled = false, updated_at = now(), version = version + 1
                where id = ? and version = ? and test_provisioned = true
                returning id, email, display_name, password_hash, role, enabled, locked, password_change_required,
                          test_provisioned, test_provisioning_reference, test_provisioned_at,
                          created_at, updated_at, version
                """, MAPPER, id, version);
    }

    private static PlatformUserAccount map(ResultSet rs, int rowNum) throws SQLException {
        return new PlatformUserAccount(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                RoleName.valueOf(rs.getString("role")),
                rs.getBoolean("enabled"),
                rs.getBoolean("locked"),
                rs.getBoolean("password_change_required"),
                rs.getBoolean("test_provisioned"),
                rs.getString("test_provisioning_reference"),
                instant(rs, "test_provisioned_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
