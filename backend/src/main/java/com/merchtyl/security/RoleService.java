package com.merchtyl.security;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditService auditService;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Role getRequiredRole(RoleName name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Security role not found: " + name));
    }

    @Transactional
    public RolePermission grantPermission(RoleName roleName, String permissionCode) {
        return grantPermission(roleName, permissionCode, null, null);
    }

    @Transactional
    public RolePermission grantPermission(RoleName roleName, String permissionCode, UUID actorUserId, String reason) {
        Role role = getRequiredRole(roleName);
        Permission permission = getRequiredPermission(permissionCode);

        if (rolePermissionRepository.existsByRoleAndPermission(role, permission)) {
            throw new ConflictException("Permission is already granted to role");
        }

        Map<String, Object> before = roleSnapshot(role);
        RolePermission rolePermission = rolePermissionRepository.save(new RolePermission(role, permission));
        auditService.record(new CreateAuditRecordCommand(
                actorUserId,
                AuditAction.PERMISSION_GRANTED,
                "ROLE",
                role.getId(),
                null,
                null,
                before,
                roleSnapshot(role, permission.getCode()),
                reason));
        return rolePermission;
    }

    @Transactional
    public void revokePermission(RoleName roleName, String permissionCode, UUID actorUserId, String reason) {
        Role role = getRequiredRole(roleName);
        Permission permission = getRequiredPermission(permissionCode);
        RolePermission rolePermission = rolePermissionRepository.findByRoleAndPermission(role, permission)
                .orElseThrow(() -> new NotFoundException("Role permission grant not found"));

        Map<String, Object> before = roleSnapshot(role);
        rolePermissionRepository.delete(rolePermission);
        auditService.record(new CreateAuditRecordCommand(
                actorUserId,
                AuditAction.PERMISSION_REVOKED,
                "ROLE",
                role.getId(),
                null,
                null,
                before,
                roleSnapshotWithout(role, permission.getCode()),
                reason));
    }

    private Permission getRequiredPermission(String permissionCode) {
        return permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new NotFoundException("Permission not found: " + permissionCode));
    }

    private Map<String, Object> roleSnapshot(Role role) {
        return Map.of(
                "role", role.getName(),
                "permissions", permissions(role));
    }

    private Map<String, Object> roleSnapshot(Role role, String additionalPermission) {
        return Map.of(
                "role", role.getName(),
                "permissions", Stream.concat(permissions(role).stream(), Stream.of(additionalPermission))
                        .distinct()
                        .sorted()
                        .toList());
    }

    private Map<String, Object> roleSnapshotWithout(Role role, String removedPermission) {
        return Map.of(
                "role", role.getName(),
                "permissions", permissions(role).stream()
                        .filter(permission -> !permission.equals(removedPermission))
                        .toList());
    }

    private List<String> permissions(Role role) {
        return rolePermissionRepository.findByRole(role).stream()
                .map(rolePermission -> rolePermission.getPermission().getCode())
                .sorted()
                .toList();
    }
}
