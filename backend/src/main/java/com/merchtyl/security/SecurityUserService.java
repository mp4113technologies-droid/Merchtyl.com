package com.merchtyl.security;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class SecurityUserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public SecurityUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public User createUser(CreateSecurityUserCommand command) {
        String email = normalizeEmail(command.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Security user email is already registered");
        }

        Role role = roleRepository.findByName(command.initialRole())
                .orElseThrow(() -> new NotFoundException("Security role not found: " + command.initialRole()));

        User user = new User(
                email,
                command.displayName().trim(),
                passwordEncoder.encode(command.rawPassword()));
        User saved = userRepository.save(user);
        userRoleRepository.save(new UserRole(saved, role));
        return saved;
    }

    @Transactional(readOnly = true)
    public User getEnabledUserByEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new NotFoundException("Security user not found"));
        if (!user.isEnabled()) {
            throw new NotFoundException("Security user not found");
        }
        return user;
    }

    @Transactional
    public User activateUser(UUID userId, UUID actorUserId, String reason) {
        User user = getRequiredUser(userId);
        Map<String, Object> before = userSnapshot(user);
        user.enable();
        auditService.record(new CreateAuditRecordCommand(
                actorUserId,
                AuditAction.USER_ACTIVATED,
                "USER",
                user.getId(),
                null,
                null,
                before,
                userSnapshot(user),
                reason));
        return user;
    }

    @Transactional
    public User deactivateUser(UUID userId, UUID actorUserId, String reason) {
        User user = getRequiredUser(userId);
        Map<String, Object> before = userSnapshot(user);
        user.disable();
        auditService.record(new CreateAuditRecordCommand(
                actorUserId,
                AuditAction.USER_DEACTIVATED,
                "USER",
                user.getId(),
                null,
                null,
                before,
                userSnapshot(user),
                reason));
        return user;
    }

    @Transactional
    public UserRole assignRole(UUID userId, RoleName roleName, UUID actorUserId, String reason) {
        User user = getRequiredUser(userId);
        Role role = getRequiredRole(roleName);
        if (userRoleRepository.existsByUserAndRole(user, role)) {
            throw new ConflictException("Role is already assigned to user");
        }

        Map<String, Object> before = userSnapshot(user);
        UserRole userRole = userRoleRepository.save(new UserRole(user, role));
        auditService.record(new CreateAuditRecordCommand(
                actorUserId,
                AuditAction.USER_ROLE_ASSIGNED,
                "USER",
                user.getId(),
                null,
                null,
                before,
                userSnapshot(user, role.getName()),
                reason));
        return userRole;
    }

    @Transactional
    public void removeRole(UUID userId, RoleName roleName, UUID actorUserId, String reason) {
        User user = getRequiredUser(userId);
        Role role = getRequiredRole(roleName);
        UserRole userRole = userRoleRepository.findByUserAndRole(user, role)
                .orElseThrow(() -> new NotFoundException("User role assignment not found"));

        Map<String, Object> before = userSnapshot(user);
        userRoleRepository.delete(userRole);
        auditService.record(new CreateAuditRecordCommand(
                actorUserId,
                AuditAction.USER_ROLE_REMOVED,
                "USER",
                user.getId(),
                null,
                null,
                before,
                userSnapshotWithout(user, role.getName()),
                reason));
    }

    private User getRequiredUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Security user not found"));
    }

    private Role getRequiredRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new NotFoundException("Security role not found: " + roleName));
    }

    private Map<String, Object> userSnapshot(User user) {
        return Map.of(
                "email", user.getEmail(),
                "displayName", user.getDisplayName(),
                "enabled", user.isEnabled(),
                "locked", user.isLocked(),
                "roles", roles(user));
    }

    private Map<String, Object> userSnapshot(User user, RoleName additionalRole) {
        return Map.of(
                "email", user.getEmail(),
                "displayName", user.getDisplayName(),
                "enabled", user.isEnabled(),
                "locked", user.isLocked(),
                "roles", roles(user, additionalRole));
    }

    private Map<String, Object> userSnapshotWithout(User user, RoleName removedRole) {
        return Map.of(
                "email", user.getEmail(),
                "displayName", user.getDisplayName(),
                "enabled", user.isEnabled(),
                "locked", user.isLocked(),
                "roles", roles(user).stream()
                        .filter(role -> role != removedRole)
                        .toList());
    }

    private List<RoleName> roles(User user) {
        return userRoleRepository.findByUser(user).stream()
                .map(userRole -> userRole.getRole().getName())
                .sorted()
                .toList();
    }

    private List<RoleName> roles(User user, RoleName additionalRole) {
        return Stream.concat(roles(user).stream(), Stream.of(additionalRole))
                .distinct()
                .sorted()
                .toList();
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim().toLowerCase();
    }
}
