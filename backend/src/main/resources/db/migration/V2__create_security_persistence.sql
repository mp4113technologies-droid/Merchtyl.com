CREATE TABLE security_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_security_users_email UNIQUE (email),
    CONSTRAINT ck_security_users_email_nonblank CHECK (length(trim(email)) > 0),
    CONSTRAINT ck_security_users_display_name_nonblank CHECK (length(trim(display_name)) > 0),
    CONSTRAINT ck_security_users_password_hash_nonblank CHECK (length(trim(password_hash)) > 0)
);

CREATE TABLE security_roles (
    id UUID PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_security_roles_name UNIQUE (name),
    CONSTRAINT ck_security_roles_name CHECK (name IN ('OWNER', 'MANAGER', 'CASHIER'))
);

CREATE TABLE security_permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_security_permissions_code UNIQUE (code),
    CONSTRAINT ck_security_permissions_code_nonblank CHECK (length(trim(code)) > 0)
);

CREATE TABLE security_user_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_security_user_roles_user FOREIGN KEY (user_id) REFERENCES security_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_security_user_roles_role FOREIGN KEY (role_id) REFERENCES security_roles (id) ON DELETE RESTRICT,
    CONSTRAINT uq_security_user_roles_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE security_role_permissions (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_security_role_permissions_role FOREIGN KEY (role_id) REFERENCES security_roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_security_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES security_permissions (id) ON DELETE CASCADE,
    CONSTRAINT uq_security_role_permissions_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE security_refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_token_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_security_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES security_users (id) ON DELETE CASCADE,
    CONSTRAINT uq_security_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_security_refresh_tokens_token_hash_nonblank CHECK (length(trim(token_hash)) > 0),
    CONSTRAINT ck_security_refresh_tokens_replaced_by_not_self CHECK (replaced_by_token_id IS NULL OR replaced_by_token_id <> id)
);

INSERT INTO security_roles (id, name, description, system_role)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'OWNER', 'Store owner with full operational access.', TRUE),
    ('00000000-0000-0000-0000-000000000102', 'MANAGER', 'Store manager with management and operational access.', TRUE),
    ('00000000-0000-0000-0000-000000000103', 'CASHIER', 'Cashier with point-of-sale operational access.', TRUE);

CREATE INDEX idx_security_user_roles_user_id ON security_user_roles (user_id);
CREATE INDEX idx_security_user_roles_role_id ON security_user_roles (role_id);
CREATE INDEX idx_security_role_permissions_role_id ON security_role_permissions (role_id);
CREATE INDEX idx_security_role_permissions_permission_id ON security_role_permissions (permission_id);
CREATE INDEX idx_security_refresh_tokens_user_id ON security_refresh_tokens (user_id);
CREATE INDEX idx_security_refresh_tokens_active_user ON security_refresh_tokens (user_id, expires_at) WHERE revoked_at IS NULL;
