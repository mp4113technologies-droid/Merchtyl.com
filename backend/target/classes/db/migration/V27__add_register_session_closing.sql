ALTER TABLE register_sessions
    ADD COLUMN counted_cash NUMERIC(12, 2),
    ADD COLUMN expected_cash_at_close NUMERIC(12, 2),
    ADD COLUMN difference_cash NUMERIC(12, 2),
    ADD COLUMN closed_by_user_id UUID,
    ADD COLUMN closed_at TIMESTAMPTZ,
    ADD COLUMN force_close_reason VARCHAR(1000),
    ADD CONSTRAINT fk_register_sessions_closed_by_user FOREIGN KEY (closed_by_user_id) REFERENCES security_users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_register_sessions_counted_cash_nonnegative CHECK (counted_cash IS NULL OR counted_cash >= 0),
    ADD CONSTRAINT ck_register_sessions_force_reason_nonblank CHECK (force_close_reason IS NULL OR btrim(force_close_reason) <> ''),
    ADD CONSTRAINT ck_register_sessions_closing_fields CHECK (
        (status IN ('OPEN', 'CLOSING') AND counted_cash IS NULL AND expected_cash_at_close IS NULL AND difference_cash IS NULL AND closed_by_user_id IS NULL AND closed_at IS NULL)
        OR (status = 'CLOSED' AND counted_cash IS NOT NULL AND expected_cash_at_close IS NOT NULL AND difference_cash IS NOT NULL AND closed_by_user_id IS NOT NULL AND closed_at IS NOT NULL AND force_close_reason IS NULL)
        OR (status = 'FORCE_CLOSED' AND counted_cash IS NOT NULL AND expected_cash_at_close IS NOT NULL AND difference_cash IS NOT NULL AND closed_by_user_id IS NOT NULL AND closed_at IS NOT NULL AND force_close_reason IS NOT NULL)
    );

CREATE INDEX idx_register_sessions_closed_at ON register_sessions (closed_at DESC);
CREATE INDEX idx_register_sessions_closed_by_user_id ON register_sessions (closed_by_user_id);

INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000226', 'REGISTER_SESSION_CLOSE', 'Close register sessions.'),
    ('00000000-0000-0000-0000-000000000227', 'REGISTER_SESSION_FORCE_CLOSE', 'Force-close register sessions.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000326'::UUID, 'OWNER', 'REGISTER_SESSION_CLOSE'),
        ('00000000-0000-0000-0000-000000000327'::UUID, 'OWNER', 'REGISTER_SESSION_FORCE_CLOSE'),
        ('00000000-0000-0000-0000-000000000424'::UUID, 'MANAGER', 'REGISTER_SESSION_CLOSE'),
        ('00000000-0000-0000-0000-000000000425'::UUID, 'MANAGER', 'REGISTER_SESSION_FORCE_CLOSE'),
        ('00000000-0000-0000-0000-000000000523'::UUID, 'CASHIER', 'REGISTER_SESSION_CLOSE')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
