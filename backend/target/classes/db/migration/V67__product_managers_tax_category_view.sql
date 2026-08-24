INSERT INTO security_role_permissions(id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':TAX_VIEW')::uuid, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'TAX_VIEW'
WHERE role.name IN ('TENANT_OWNER', 'STORE_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
