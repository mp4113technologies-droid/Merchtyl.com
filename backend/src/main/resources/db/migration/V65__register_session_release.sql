ALTER TABLE register_session_operator_history
    DROP CONSTRAINT ck_register_operator_history_change_type;

ALTER TABLE register_session_operator_history
    ADD CONSTRAINT ck_register_operator_history_change_type
        CHECK (change_type IN ('TRANSFER', 'OVERRIDE', 'RELEASE'));

INSERT INTO security_permissions (id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000233', 'REGISTER_SESSION_RELEASE', 'Release an open register session to an eligible cashier.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'REGISTER_SESSION_RELEASE'
WHERE role.name IN ('OWNER', 'TENANT_OWNER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
