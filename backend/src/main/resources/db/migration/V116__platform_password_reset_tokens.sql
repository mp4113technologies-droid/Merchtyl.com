CREATE TABLE platform_password_reset_tokens (
    id UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(40) NOT NULL DEFAULT 'PASSWORD_RESET',
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    request_ip VARCHAR(64),
    correlation_id VARCHAR(128),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_platform_password_reset_purpose CHECK (purpose = 'PASSWORD_RESET')
);

CREATE INDEX idx_platform_password_reset_user
    ON platform_password_reset_tokens(platform_user_id, created_at DESC);
CREATE INDEX idx_platform_password_reset_unused
    ON platform_password_reset_tokens(platform_user_id, used_at, revoked_at);
