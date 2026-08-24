package com.merchtyl.auth;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.config.JwtProperties;
import com.merchtyl.security.CreateSecurityUserCommand;
import com.merchtyl.security.RefreshToken;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.security.Role;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.SecurityUserService;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRole;
import com.merchtyl.security.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final SecurityUserService securityUserService = mock(SecurityUserService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = mock(JwtService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AuthService authService = new AuthService(
            userRepository,
            userRoleRepository,
            securityUserService,
            passwordEncoder,
            jwtService,
            refreshTokenService,
            new JwtProperties("merchtyl-test", "change-this-development-secret-change-this-development-secret", 15, 7),
            auditService);

    @Test
    void registerUsesSerializableTransactionForBootstrapOwnerRaceProtection() throws Exception {
        Method register = AuthService.class.getMethod("register", RegisterRequest.class);

        Transactional transactional = register.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation()).isEqualTo(Isolation.SERIALIZABLE);
    }

    @Test
    void registerCreatesOnlyInitialOwnerAccount() {
        User user = enabledUser("owner@example.local", "OwnerDev!2026");
        stubRoles(user, RoleName.OWNER);
        when(userRepository.existsByEmailIgnoreCase("owner@example.local")).thenReturn(false);
        when(userRepository.count()).thenReturn(0L);
        when(securityUserService.createUser(any(CreateSecurityUserCommand.class))).thenReturn(user);
        when(refreshTokenService.generateRawToken()).thenReturn("refresh-token");
        when(refreshTokenService.createRefreshToken(any(User.class), any(String.class), any(Instant.class)))
                .thenReturn(new RefreshToken(user, "hash", Instant.now().plusSeconds(3600)));
        when(jwtService.issueAccessToken(any(User.class), anyList(), any(Instant.class), any(Instant.class)))
                .thenReturn("access-token");

        AuthResponse response = authService.register(new RegisterRequest(
                " owner@example.local ",
                "OwnerDev!2026",
                "Owner Dev"));

        ArgumentCaptor<CreateSecurityUserCommand> command =
                ArgumentCaptor.forClass(CreateSecurityUserCommand.class);
        verify(securityUserService).createUser(command.capture());
        assertThat(command.getValue().email()).isEqualTo("owner@example.local");
        assertThat(command.getValue().initialRole()).isEqualTo(RoleName.OWNER);
        assertThat(response.roles()).containsExactly(RoleName.OWNER);
    }

    @Test
    void registerRejectsSelfRegistrationAfterOwnerBootstrap() {
        when(userRepository.existsByEmailIgnoreCase("cashier@example.local")).thenReturn(false);
        when(userRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "cashier@example.local",
                "CashierDev!2026",
                "Cashier Dev")))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(securityUserService, never()).createUser(any());
        verify(refreshTokenService, never()).createRefreshToken(any(), any(), any());
    }

    @Test
    void loginVerifiesPasswordAndIssuesAccessAndRefreshTokens() {
        User user = enabledUser("owner@example.local", "OwnerDev!2026");
        stubRoles(user, RoleName.OWNER);
        when(userRepository.findByEmailIgnoreCase("owner@example.local")).thenReturn(Optional.of(user));
        when(refreshTokenService.generateRawToken()).thenReturn("refresh-token");
        when(refreshTokenService.createRefreshToken(any(User.class), any(String.class), any(Instant.class)))
                .thenReturn(new RefreshToken(user, "hash", Instant.now().plusSeconds(3600)));
        when(jwtService.issueAccessToken(any(User.class), anyList(), any(Instant.class), any(Instant.class)))
                .thenReturn("access-token");

        AuthResponse response = authService.login(new LoginRequest(" owner@example.local ", "OwnerDev!2026"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.roles()).containsExactly(RoleName.OWNER);

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOGIN_SUCCESS);
        assertThat(audit.getValue().afterSnapshot().toString())
                .doesNotContain("OwnerDev!2026")
                .doesNotContain("access-token")
                .doesNotContain("refresh-token");
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = enabledUser("manager@example.local", "ManagerDev!2026");
        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("manager@example.local", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenService, never()).createRefreshToken(any(), any(), any());
        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOGIN_FAILURE);
        assertThat(audit.getValue().reason()).isEqualTo("bad_credentials");
        assertThat(audit.getValue().afterSnapshot().toString())
                .doesNotContain("wrong-password");
    }

    @Test
    void loginRejectsDisabledUser() {
        User user = enabledUser("cashier@example.local", "CashierDev!2026");
        user.disable();
        when(userRepository.findByEmailIgnoreCase("cashier@example.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("cashier@example.local", "CashierDev!2026")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshRotatesTokenAndRevokesPresentedToken() {
        User user = enabledUser("owner@example.local", "OwnerDev!2026");
        RefreshToken presented = new RefreshToken(user, "old-hash", Instant.now().plusSeconds(3600));
        RefreshToken replacement = new RefreshToken(user, "new-hash", Instant.now().plusSeconds(7200));
        stubRoles(user, RoleName.OWNER);
        when(refreshTokenService.findByRawToken("old-refresh")).thenReturn(Optional.of(presented));
        when(refreshTokenService.generateRawToken()).thenReturn("new-refresh");
        when(refreshTokenService.createRefreshToken(any(User.class), any(String.class), any(Instant.class)))
                .thenReturn(replacement);
        when(jwtService.issueAccessToken(any(User.class), anyList(), any(Instant.class), any(Instant.class)))
                .thenReturn("new-access");

        AuthResponse response = authService.refresh(new RefreshRequest("old-refresh"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(presented.getRevokedAt()).isNotNull();
        assertThat(presented.getReplacedByTokenId()).isEqualTo(replacement.getId());
    }

    @Test
    void reusingRevokedRefreshTokenRevokesActiveTokensForUser() {
        User user = enabledUser("owner@example.local", "OwnerDev!2026");
        RefreshToken revoked = new RefreshToken(user, "old-hash", Instant.now().plusSeconds(3600));
        revoked.revoke(Instant.now(), null);
        when(refreshTokenService.findByRawToken("old-refresh")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old-refresh")))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<Instant> revokedAt = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenService).revokeActiveTokensForUser(same(user), revokedAt.capture());
        assertThat(revokedAt.getValue()).isNotNull();
    }

    @Test
    void logoutRevokesActiveRefreshToken() {
        User user = enabledUser("cashier@example.local", "CashierDev!2026");
        RefreshToken token = new RefreshToken(user, "hash", Instant.now().plusSeconds(3600));
        when(refreshTokenService.findByRawToken("refresh-token")).thenReturn(Optional.of(token));

        authService.logout(new LogoutRequest("refresh-token"));

        assertThat(token.getRevokedAt()).isNotNull();
        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.LOGOUT);
        assertThat(audit.getValue().afterSnapshot().toString()).doesNotContain("refresh-token");
    }

    @Test
    void currentUserReturnsAuthenticatedUser() {
        User user = enabledUser("manager@example.local", "ManagerDev!2026");
        stubRoles(user, RoleName.MANAGER);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("manager@example.local");
        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(user));

        CurrentUserResponse response = authService.currentUser(authentication);

        assertThat(response.email()).isEqualTo("manager@example.local");
        assertThat(response.roles()).containsExactly(RoleName.MANAGER);
    }

    private User enabledUser(String email, String password) {
        return new User(email, email.substring(0, email.indexOf('@')), passwordEncoder.encode(password));
    }

    private void stubRoles(User user, RoleName roleName) {
        Role role = new Role(roleName, roleName.name(), true);
        when(userRoleRepository.findByUser(user)).thenReturn(List.of(new UserRole(user, role)));
    }
}
