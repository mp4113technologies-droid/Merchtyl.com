CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_accounts_email UNIQUE (email),
    CONSTRAINT ck_user_accounts_role CHECK (role IN ('ADMIN', 'MANAGER', 'CASHIER')),
    CONSTRAINT ck_user_accounts_email_nonblank CHECK (length(trim(email)) > 0),
    CONSTRAINT ck_user_accounts_display_name_nonblank CHECK (length(trim(display_name)) > 0)
);

CREATE INDEX idx_user_accounts_role ON user_accounts (role);
