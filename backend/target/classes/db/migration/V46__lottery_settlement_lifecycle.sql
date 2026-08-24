ALTER TABLE lottery_settlements
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'CALCULATED',
    ADD COLUMN approved_by UUID,
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN posted_by UUID,
    ADD COLUMN posted_at TIMESTAMPTZ,
    ADD COLUMN reopened_by UUID,
    ADD COLUMN reopened_at TIMESTAMPTZ,
    ADD COLUMN reopen_reason VARCHAR(1000),
    ADD COLUMN lifecycle_notes VARCHAR(1000);

ALTER TABLE lottery_settlements
    ADD CONSTRAINT fk_lottery_settlements_approved_by FOREIGN KEY (approved_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_lottery_settlements_posted_by FOREIGN KEY (posted_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_lottery_settlements_reopened_by FOREIGN KEY (reopened_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_lottery_settlements_status CHECK (status IN ('DRAFT', 'CALCULATED', 'UNDER_REVIEW', 'APPROVED', 'POSTED', 'REOPENED'));

CREATE INDEX idx_lottery_settlements_status ON lottery_settlements (status);

INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000247', 'LOTTERY_SETTLEMENT_APPROVE', 'Approve and reopen lottery settlements.'),
    ('00000000-0000-0000-0000-000000000248', 'LOTTERY_SETTLEMENT_POST', 'Post approved lottery settlements.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000347'::UUID, 'OWNER', 'LOTTERY_SETTLEMENT_APPROVE'),
        ('00000000-0000-0000-0000-000000000447'::UUID, 'MANAGER', 'LOTTERY_SETTLEMENT_APPROVE'),
        ('00000000-0000-0000-0000-000000000348'::UUID, 'OWNER', 'LOTTERY_SETTLEMENT_POST')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
