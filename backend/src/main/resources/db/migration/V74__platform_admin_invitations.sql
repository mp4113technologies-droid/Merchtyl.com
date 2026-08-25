ALTER TABLE platform_users
    ADD COLUMN first_name VARCHAR(80),
    ADD COLUMN last_name VARCHAR(80),
    ADD COLUMN status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN created_by_platform_user_id UUID,
    ADD COLUMN last_login_at TIMESTAMPTZ;

ALTER TABLE platform_users DROP CONSTRAINT ck_platform_users_password_hash_nonblank;
ALTER TABLE platform_users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE platform_users
    ADD CONSTRAINT ck_platform_users_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'DEACTIVATED')),
    ADD CONSTRAINT fk_platform_users_created_by FOREIGN KEY (created_by_platform_user_id) REFERENCES platform_users(id) ON DELETE SET NULL;

UPDATE platform_users
SET first_name = split_part(display_name, ' ', 1),
    last_name = CASE WHEN position(' ' IN display_name) > 0 THEN substring(display_name FROM position(' ' IN display_name) + 1) ELSE '' END,
    status = CASE WHEN enabled THEN 'ACTIVE' ELSE 'DEACTIVATED' END;

CREATE TABLE platform_admin_invitations (
    id UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_by_platform_user_id UUID REFERENCES platform_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_platform_admin_invitation_purpose CHECK (purpose = 'PLATFORM_ADMIN_ACTIVATION'),
    CONSTRAINT ck_platform_admin_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED'))
);

CREATE UNIQUE INDEX uq_platform_admin_pending_invitation
    ON platform_admin_invitations(platform_user_id) WHERE status = 'PENDING';

INSERT INTO security_permissions (id, code, description) VALUES
    (md5('permission:PLATFORM_ADMIN_VIEW')::UUID, 'PLATFORM_ADMIN_VIEW', 'View platform administrators.'),
    (md5('permission:PLATFORM_ADMIN_CREATE')::UUID, 'PLATFORM_ADMIN_CREATE', 'Invite platform administrators.'),
    (md5('permission:PLATFORM_ADMIN_UPDATE')::UUID, 'PLATFORM_ADMIN_UPDATE', 'Update platform administrators.'),
    (md5('permission:PLATFORM_ADMIN_ACTIVATE')::UUID, 'PLATFORM_ADMIN_ACTIVATE', 'Reactivate platform administrators.'),
    (md5('permission:PLATFORM_ADMIN_DEACTIVATE')::UUID, 'PLATFORM_ADMIN_DEACTIVATE', 'Deactivate platform administrators.'),
    (md5('permission:PLATFORM_ADMIN_UNLOCK')::UUID, 'PLATFORM_ADMIN_UNLOCK', 'Unlock platform administrators.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code LIKE 'PLATFORM_ADMIN_%'
WHERE role.name = 'PLATFORM_SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'PLATFORM_ADMIN_VIEW'
WHERE role.name = 'PLATFORM_SUPPORT_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
