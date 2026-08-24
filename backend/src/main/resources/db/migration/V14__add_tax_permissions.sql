INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000220', 'TAX_VIEW', 'View tax geography and tax configuration.'),
    ('00000000-0000-0000-0000-000000000221', 'TAX_MANAGE', 'Create and manage tax geography and tax configuration.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000320'::UUID, 'OWNER', 'TAX_VIEW'),
        ('00000000-0000-0000-0000-000000000321'::UUID, 'OWNER', 'TAX_MANAGE'),
        ('00000000-0000-0000-0000-000000000418'::UUID, 'MANAGER', 'TAX_VIEW'),
        ('00000000-0000-0000-0000-000000000419'::UUID, 'MANAGER', 'TAX_MANAGE')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
