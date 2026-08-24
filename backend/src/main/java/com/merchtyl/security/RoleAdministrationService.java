package com.merchtyl.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class RoleAdministrationService {
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public RoleAdministrationService(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(role -> new RoleResponse(
                        role.getId(),
                        role.getName(),
                        role.getDescription(),
                        role.isSystemRole(),
                        permissions(role),
                        role.getVersion()))
                .toList();
    }

    private List<String> permissions(Role role) {
        return rolePermissionRepository.findByRole(role).stream()
                .map(rolePermission -> rolePermission.getPermission().getCode())
                .sorted()
                .toList();
    }
}
