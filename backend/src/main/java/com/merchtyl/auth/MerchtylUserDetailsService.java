package com.merchtyl.auth;

import com.merchtyl.security.RolePermissionRepository;
import com.merchtyl.security.AuthorizationService;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRoleRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;

@Service
public class MerchtylUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public MerchtylUserDetailsService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        var user = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY));
        userRoleRepository.findByUser(user).stream()
                .map(userRole -> userRole.getRole().getName())
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .forEach(authorities::add);
        rolePermissionRepository.findPermissionCodesByUser(user).stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .accountLocked(user.isLocked())
                .build();
    }
}
