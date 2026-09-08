INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'POS_SALE_DISCOUNT'
WHERE role.name = 'KITCHEN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
