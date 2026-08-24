ALTER TABLE tenant_owner_invitations DROP CONSTRAINT IF EXISTS ck_tenant_owner_invitations_status;

UPDATE tenant_owner_invitations
SET status = CASE status
    WHEN 'ACCEPTED' THEN 'USED'
    WHEN 'REVOKED' THEN 'INVALIDATED'
    ELSE status
END;

ALTER TABLE tenant_owner_invitations
    ADD COLUMN IF NOT EXISTS invalidated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS invalidation_reason VARCHAR(1000);

ALTER TABLE tenant_owner_invitations
    ADD CONSTRAINT ck_tenant_owner_invitations_status CHECK (status IN (
        'PENDING',
        'SENT',
        'EXPIRED',
        'USED',
        'INVALIDATED',
        'CANCELLED'
    ));

DROP INDEX IF EXISTS uq_tenant_owner_invitations_active_email;

CREATE UNIQUE INDEX uq_tenant_owner_invitations_active_email
    ON tenant_owner_invitations (tenant_id, lower(email))
    WHERE status IN ('PENDING', 'SENT');

ALTER TABLE email_deliveries
    ADD COLUMN IF NOT EXISTS requested_by_platform_user_id UUID,
    ADD COLUMN IF NOT EXISTS requested_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS requested_notes VARCHAR(2000);

ALTER TABLE email_deliveries
    ADD CONSTRAINT fk_email_deliveries_requested_by_platform_user
    FOREIGN KEY (requested_by_platform_user_id) REFERENCES platform_users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_email_deliveries_requested_by
    ON email_deliveries(requested_by_platform_user_id, created_at DESC);
