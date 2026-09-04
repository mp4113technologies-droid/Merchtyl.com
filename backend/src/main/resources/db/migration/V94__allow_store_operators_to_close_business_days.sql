INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'BUSINESS_DAY_CLOSE'
WHERE role.name IN ('CASHIER', 'KITCHEN')
  AND EXISTS (
      SELECT 1
      FROM security_role_permissions register_open_grant
      JOIN security_permissions register_open_permission
        ON register_open_permission.id = register_open_grant.permission_id
      WHERE register_open_grant.role_id = role.id
        AND register_open_permission.code = 'REGISTER_SESSION_OPEN'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
