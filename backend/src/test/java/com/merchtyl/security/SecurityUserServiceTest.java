package com.merchtyl.security;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityUserServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuditService auditService = mock(AuditService.class);
    private final SecurityUserService service = new SecurityUserService(
            userRepository,
            roleRepository,
            userRoleRepository,
            passwordEncoder,
            auditService);

    @Test
    void createUserHashesPasswordNormalizesEmailAndAssignsRole() {
        Role owner = new Role(RoleName.OWNER, "Owner", true);
        when(userRepository.existsByEmailIgnoreCase("owner@example.local")).thenReturn(false);
        when(roleRepository.findByName(RoleName.OWNER)).thenReturn(Optional.of(owner));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = service.createUser(new CreateSecurityUserCommand(
                " Owner@Example.Local ",
                " Store Owner ",
                "OwnerDev!2026",
                RoleName.OWNER));

        assertThat(saved.getEmail()).isEqualTo("owner@example.local");
        assertThat(saved.getDisplayName()).isEqualTo("Store Owner");
        assertThat(saved.getPasswordHash()).isNotEqualTo("OwnerDev!2026");
        assertThat(passwordEncoder.matches("OwnerDev!2026", saved.getPasswordHash())).isTrue();
        verify(userRoleRepository).save(argThat(userRole ->
                userRole.getUser() == saved && userRole.getRole() == owner));
    }

    @Test
    void createUserRejectsDuplicateEmailBeforeHashingOrAssigningRole() {
        when(userRepository.existsByEmailIgnoreCase("owner@example.local")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(new CreateSecurityUserCommand(
                "owner@example.local",
                "Owner",
                "OwnerDev!2026",
                RoleName.OWNER)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");

        verify(roleRepository, never()).findByName(any());
        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void deactivateUserAuditsStatusChangeWithoutPasswordHash() {
        User user = new User("cashier@example.local", "Cashier", "password-hash");
        UUID actorUserId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUser(user)).thenReturn(java.util.List.of());

        service.deactivateUser(user.getId(), actorUserId, "employment ended");

        assertThat(user.isEnabled()).isFalse();
        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().actorUserId()).isEqualTo(actorUserId);
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.USER_DEACTIVATED);
        assertThat(audit.getValue().afterSnapshot().toString())
                .contains("enabled=false")
                .doesNotContain("password-hash");
    }

    @Test
    void assignRoleAuditsRoleChange() {
        User user = new User("manager@example.local", "Manager", "password-hash");
        Role manager = new Role(RoleName.MANAGER, "Manager", true);
        UUID actorUserId = UUID.fromString("00000000-0000-0000-0000-000000000702");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(roleRepository.findByName(RoleName.MANAGER)).thenReturn(Optional.of(manager));
        when(userRoleRepository.existsByUserAndRole(user, manager)).thenReturn(false);
        when(userRoleRepository.findByUser(user)).thenReturn(java.util.List.of());
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.assignRole(user.getId(), RoleName.MANAGER, actorUserId, "promotion");

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.USER_ROLE_ASSIGNED);
        assertThat(audit.getValue().afterSnapshot().toString()).contains("MANAGER");
    }
}
