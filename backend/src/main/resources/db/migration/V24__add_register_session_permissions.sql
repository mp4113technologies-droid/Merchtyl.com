INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000222', 'REGISTER_SESSION_OPEN', 'Open register sessions.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000322'::UUID, 'OWNER', 'REGISTER_SESSION_OPEN'),
        ('00000000-0000-0000-0000-000000000420'::UUID, 'MANAGER', 'REGISTER_SESSION_OPEN'),
        ('00000000-0000-0000-0000-000000000520'::UUID, 'CASHIER', 'REGISTER_SESSION_OPEN')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
