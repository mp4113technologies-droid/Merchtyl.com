package com.merchtyl.security;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceTest {
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final RoleService roleService = new RoleService(
            roleRepository,
            permissionRepository,
            rolePermissionRepository,
            auditService);

    @Test
    void grantPermissionAuditsPermissionChange() {
        Role manager = new Role(RoleName.MANAGER, "Manager", true);
        Permission productView = new Permission("PRODUCT_VIEW", "View products");
        Permission productManage = new Permission("PRODUCT_MANAGE", "Manage products");
        UUID actorUserId = UUID.fromString("00000000-0000-0000-0000-000000000703");
        when(roleRepository.findByName(RoleName.MANAGER)).thenReturn(Optional.of(manager));
        when(permissionRepository.findByCode("PRODUCT_MANAGE")).thenReturn(Optional.of(productManage));
        when(rolePermissionRepository.existsByRoleAndPermission(manager, productManage)).thenReturn(false);
        when(rolePermissionRepository.findByRole(manager)).thenReturn(List.of(new RolePermission(manager, productView)));
        when(rolePermissionRepository.save(any(RolePermission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RolePermission saved = roleService.grantPermission(RoleName.MANAGER, "PRODUCT_MANAGE", actorUserId, "expanded duties");

        assertThat(saved.getPermission()).isEqualTo(productManage);
        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.PERMISSION_GRANTED);
        assertThat(audit.getValue().actorUserId()).isEqualTo(actorUserId);
        assertThat(audit.getValue().afterSnapshot().toString()).contains("PRODUCT_MANAGE");
    }
}
