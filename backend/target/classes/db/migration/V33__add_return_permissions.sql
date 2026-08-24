INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-00000000023a', 'RETURN_VIEW', 'View returns.'),
    ('00000000-0000-0000-0000-00000000023b', 'RETURN_CREATE', 'Create returns.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-00000000033a'::UUID, 'OWNER', 'RETURN_VIEW'),
        ('00000000-0000-0000-0000-00000000033b'::UUID, 'OWNER', 'RETURN_CREATE'),
        ('00000000-0000-0000-0000-00000000043a'::UUID, 'MANAGER', 'RETURN_VIEW'),
        ('00000000-0000-0000-0000-00000000043b'::UUID, 'MANAGER', 'RETURN_CREATE'),
        ('00000000-0000-0000-0000-00000000053a'::UUID, 'CASHIER', 'RETURN_VIEW')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
