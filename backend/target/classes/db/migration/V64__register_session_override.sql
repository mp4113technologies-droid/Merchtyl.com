ALTER TABLE register_session_operator_history
    ADD COLUMN change_type VARCHAR(32) NOT NULL DEFAULT 'TRANSFER';

ALTER TABLE register_session_operator_history
    ADD CONSTRAINT ck_register_operator_history_change_type
        CHECK (change_type IN ('TRANSFER', 'OVERRIDE'));

INSERT INTO security_permissions (id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000232', 'REGISTER_SESSION_OVERRIDE', 'Explicitly override the current register operator.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'REGISTER_SESSION_OVERRIDE'
WHERE role.name IN ('OWNER', 'TENANT_OWNER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
