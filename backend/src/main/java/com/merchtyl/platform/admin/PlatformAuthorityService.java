package com.merchtyl.platform.admin;

import com.merchtyl.security.AuthorizationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlatformAuthorityService {
    private final JdbcTemplate jdbcTemplate;

    public PlatformAuthorityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SimpleGrantedAuthority> authorities(PlatformUserAccount user) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority(AuthorizationService.PLATFORM_SCOPE_AUTHORITY));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        jdbcTemplate.queryForList("""
                select distinct permission.code
                from security_role_permissions role_permission
                join security_roles role on role.id = role_permission.role_id
                join security_permissions permission on permission.id = role_permission.permission_id
                where role.name = ?
                order by permission.code
                """, String.class, user.role().name())
                .forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        return authorities;
    }
}
