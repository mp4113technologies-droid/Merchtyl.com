INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-00000000023c', 'REFUND_VIEW', 'View refunds.'),
    ('00000000-0000-0000-0000-00000000023d', 'REFUND_APPROVE', 'Approve configured refund operations.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-00000000033c'::UUID, 'OWNER', 'REFUND_VIEW'),
        ('00000000-0000-0000-0000-00000000033d'::UUID, 'OWNER', 'REFUND_APPROVE'),
        ('00000000-0000-0000-0000-00000000043c'::UUID, 'MANAGER', 'REFUND_VIEW'),
        ('00000000-0000-0000-0000-00000000043d'::UUID, 'MANAGER', 'REFUND_APPROVE'),
        ('00000000-0000-0000-0000-00000000053c'::UUID, 'CASHIER', 'REFUND_VIEW')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
