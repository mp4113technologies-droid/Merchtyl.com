package com.merchtyl.auth;

import com.merchtyl.security.Role;
import com.merchtyl.security.AuthorizationService;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.RolePermissionRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRole;
import com.merchtyl.security.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchtylUserDetailsServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
    private final MerchtylUserDetailsService userDetailsService = new MerchtylUserDetailsService(
            userRepository,
            userRoleRepository,
            rolePermissionRepository);

    @Test
    void loadUserByUsernameIncludesRoleAndPermissionAuthorities() {
        User user = new User("manager@merchtyl.test", "Manager", "password-hash");
        Role manager = new Role(RoleName.MANAGER, "Manager", true);
        when(userRepository.findByEmailIgnoreCase("manager@merchtyl.test")).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUser(user)).thenReturn(List.of(new UserRole(user, manager)));
        when(rolePermissionRepository.findPermissionCodesByUser(user)).thenReturn(List.of(
                "PRODUCT_MANAGE",
                "PRODUCT_VIEW",
                "REPORT_VIEW"));

        var userDetails = userDetailsService.loadUserByUsername("manager@merchtyl.test");

        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder(
                        "ROLE_MANAGER",
                        AuthorizationService.TENANT_SCOPE_AUTHORITY,
                        "PRODUCT_MANAGE",
                        "PRODUCT_VIEW",
                        "REPORT_VIEW");
    }

    @Test
    void loadUserByUsernameRejectsUnknownUser() {
        when(userRepository.findByEmailIgnoreCase("missing@merchtyl.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing@merchtyl.test"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found");
    }
}
