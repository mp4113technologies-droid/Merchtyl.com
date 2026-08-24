ALTER TABLE security_users
    ADD COLUMN IF NOT EXISTS test_provisioned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS test_provisioning_reference VARCHAR(160),
    ADD COLUMN IF NOT EXISTS test_provisioned_at TIMESTAMPTZ;

ALTER TABLE platform_users
    ADD COLUMN IF NOT EXISTS test_provisioned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS test_provisioning_reference VARCHAR(160),
    ADD COLUMN IF NOT EXISTS test_provisioned_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_security_users_test_provisioned
    ON security_users (test_provisioned, tenant_id, email);

CREATE INDEX IF NOT EXISTS idx_platform_users_test_provisioned
    ON platform_users (test_provisioned, email);
