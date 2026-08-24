ALTER TABLE register_sessions
    ADD COLUMN opened_by_user_id UUID;

UPDATE register_sessions
SET opened_by_user_id = assigned_cashier_id
WHERE opened_by_user_id IS NULL;

ALTER TABLE register_sessions
    ADD CONSTRAINT fk_register_sessions_opened_by_user
        FOREIGN KEY (opened_by_user_id) REFERENCES security_users(id) ON DELETE RESTRICT;

CREATE INDEX idx_register_sessions_opened_by_user_id ON register_sessions(opened_by_user_id);

CREATE UNIQUE INDEX uq_register_sessions_open_operator
    ON register_sessions(assigned_cashier_id)
    WHERE status = 'OPEN';

CREATE TABLE register_session_operator_history (
    id UUID PRIMARY KEY,
    register_session_id UUID NOT NULL REFERENCES register_sessions(id) ON DELETE RESTRICT,
    previous_operator_user_id UUID NOT NULL REFERENCES security_users(id) ON DELETE RESTRICT,
    new_operator_user_id UUID NOT NULL REFERENCES security_users(id) ON DELETE RESTRICT,
    transferred_by_user_id UUID NOT NULL REFERENCES security_users(id) ON DELETE RESTRICT,
    reason VARCHAR(1000) NOT NULL,
    transferred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_register_operator_history_reason CHECK (btrim(reason) <> '')
);

CREATE INDEX idx_register_operator_history_session ON register_session_operator_history(register_session_id, transferred_at DESC);

INSERT INTO security_permissions (id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000228', 'REGISTER_SESSION_VIEW', 'View register sessions.'),
    ('00000000-0000-0000-0000-000000000229', 'REGISTER_SESSION_OPERATE', 'Operate an authorized register session.'),
    ('00000000-0000-0000-0000-000000000230', 'REGISTER_SESSION_VIEW_OPERATOR', 'View the current register operator.'),
    ('00000000-0000-0000-0000-000000000231', 'REGISTER_SESSION_TRANSFER', 'Transfer an open register session operator.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN ('REGISTER_SESSION_VIEW','REGISTER_SESSION_OPERATE','REGISTER_SESSION_VIEW_OPERATOR','REGISTER_SESSION_TRANSFER')
WHERE role.name IN ('OWNER','TENANT_OWNER','MANAGER','STORE_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN ('REGISTER_SESSION_VIEW','REGISTER_SESSION_OPERATE')
WHERE role.name = 'CASHIER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
