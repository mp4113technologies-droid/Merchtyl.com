CREATE TABLE email_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    invitation_id UUID,
    recipient VARCHAR(320) NOT NULL,
    template_code VARCHAR(80) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_message_id VARCHAR(180),
    status VARCHAR(40) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,
    failure_code VARCHAR(120),
    failure_message_sanitized VARCHAR(1000),
    correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_email_deliveries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE SET NULL,
    CONSTRAINT fk_email_deliveries_invitation FOREIGN KEY (invitation_id) REFERENCES tenant_owner_invitations(id) ON DELETE SET NULL,
    CONSTRAINT ck_email_deliveries_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'RETRY_SCHEDULED', 'CANCELLED')),
    CONSTRAINT ck_email_deliveries_provider CHECK (provider IN ('CONSOLE', 'RESEND')),
    CONSTRAINT ck_email_deliveries_recipient_nonblank CHECK (length(trim(recipient)) > 0),
    CONSTRAINT ck_email_deliveries_template_nonblank CHECK (length(trim(template_code)) > 0)
);

CREATE INDEX idx_email_deliveries_tenant ON email_deliveries(tenant_id, created_at DESC);
CREATE INDEX idx_email_deliveries_invitation ON email_deliveries(invitation_id);
CREATE INDEX idx_email_deliveries_status_retry ON email_deliveries(status, next_retry_at);
CREATE INDEX idx_email_deliveries_provider_message ON email_deliveries(provider, provider_message_id);

INSERT INTO security_permissions (id, code, description)
VALUES
    (md5('permission:EMAIL_DELIVERY_VIEW')::UUID, 'EMAIL_DELIVERY_VIEW', 'View transactional email delivery history.'),
    (md5('permission:EMAIL_DELIVERY_RETRY')::UUID, 'EMAIL_DELIVERY_RETRY', 'Retry failed or retry-scheduled transactional email deliveries.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN ('EMAIL_DELIVERY_VIEW', 'EMAIL_DELIVERY_RETRY')
WHERE role.name = 'PLATFORM_SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
