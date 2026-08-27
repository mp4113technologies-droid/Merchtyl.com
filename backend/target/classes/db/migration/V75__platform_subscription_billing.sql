CREATE SEQUENCE platform_invoice_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE platform_pricing_plans (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(20) NOT NULL,
    billing_interval VARCHAR(20) NOT NULL,
    base_price NUMERIC(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    trial_days INTEGER NOT NULL DEFAULT 0,
    included_stores INTEGER,
    included_registers INTEGER,
    included_users INTEGER,
    additional_store_price NUMERIC(19, 4),
    additional_register_price NUMERIC(19, 4),
    additional_user_price NUMERIC(19, 4),
    tax_behavior VARCHAR(20) NOT NULL DEFAULT 'EXCLUSIVE',
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_platform_pricing_plans_code UNIQUE (code),
    CONSTRAINT ck_platform_pricing_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_platform_pricing_plan_interval CHECK (billing_interval IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT ck_platform_pricing_plan_tax_behavior CHECK (tax_behavior IN ('EXCLUSIVE', 'INCLUSIVE', 'EXEMPT')),
    CONSTRAINT ck_platform_pricing_plan_amounts CHECK (
        base_price >= 0 AND trial_days >= 0 AND
        (included_stores IS NULL OR included_stores >= 0) AND
        (included_registers IS NULL OR included_registers >= 0) AND
        (included_users IS NULL OR included_users >= 0) AND
        (additional_store_price IS NULL OR additional_store_price >= 0) AND
        (additional_register_price IS NULL OR additional_register_price >= 0) AND
        (additional_user_price IS NULL OR additional_user_price >= 0)
    )
);

CREATE TABLE platform_pricing_plan_versions (
    id UUID PRIMARY KEY,
    pricing_plan_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    snapshot JSONB NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_platform_pricing_plan_versions UNIQUE (pricing_plan_id, version_number),
    CONSTRAINT fk_platform_pricing_plan_versions_plan FOREIGN KEY (pricing_plan_id) REFERENCES platform_pricing_plans(id)
);

ALTER TABLE tenant_subscriptions
    DROP CONSTRAINT ck_tenant_subscriptions_status,
    ADD COLUMN pricing_plan_id UUID,
    ADD COLUMN billing_interval VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN current_period_start DATE,
    ADD COLUMN current_period_end DATE,
    ADD COLUMN next_billing_date DATE,
    ADD COLUMN cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cancellation_reason VARCHAR(1000),
    ADD COLUMN cancel_requested_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by UUID,
    ADD COLUMN base_price_snapshot NUMERIC(19, 4),
    ADD COLUMN currency_code VARCHAR(3),
    ADD COLUMN custom_base_price NUMERIC(19, 4),
    ADD COLUMN custom_additional_store_price NUMERIC(19, 4),
    ADD COLUMN custom_additional_register_price NUMERIC(19, 4),
    ADD COLUMN custom_additional_user_price NUMERIC(19, 4),
    ADD COLUMN discount_name VARCHAR(180),
    ADD COLUMN discount_type VARCHAR(20),
    ADD COLUMN discount_value NUMERIC(19, 4),
    ADD COLUMN pricing_notes VARCHAR(2000),
    ADD COLUMN pricing_effective_from DATE,
    ADD COLUMN pricing_effective_until DATE,
    ADD COLUMN payment_terms_days INTEGER,
    ADD COLUMN external_payment_provider VARCHAR(80),
    ADD COLUMN external_customer_id VARCHAR(180),
    ADD COLUMN external_subscription_id VARCHAR(180),
    ADD CONSTRAINT fk_tenant_subscriptions_pricing_plan FOREIGN KEY (pricing_plan_id) REFERENCES platform_pricing_plans(id),
    ADD CONSTRAINT ck_tenant_subscriptions_status CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'PAUSED', 'CANCELLED', 'EXPIRED', 'SUSPENDED')),
    ADD CONSTRAINT ck_tenant_subscriptions_interval CHECK (billing_interval IN ('MONTHLY', 'YEARLY')),
    ADD CONSTRAINT ck_tenant_subscriptions_discount CHECK (
        discount_type IS NULL OR
        (discount_type = 'FIXED_AMOUNT' AND discount_value >= 0) OR
        (discount_type = 'PERCENTAGE' AND discount_value BETWEEN 0 AND 100)
    );

CREATE INDEX idx_tenant_subscriptions_status_billing ON tenant_subscriptions(status, next_billing_date);
CREATE INDEX idx_tenant_subscriptions_plan ON tenant_subscriptions(pricing_plan_id);

CREATE TABLE merchant_billing_contacts (
    tenant_id UUID PRIMARY KEY,
    contact_name VARCHAR(180),
    billing_email VARCHAR(320) NOT NULL,
    billing_phone VARCHAR(40),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(120),
    province_state VARCHAR(120),
    postal_code VARCHAR(32),
    country_code VARCHAR(2),
    tax_rule_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_merchant_billing_contact_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE TABLE platform_billing_tax_rules (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    label VARCHAR(120) NOT NULL,
    rate NUMERIC(9, 6) NOT NULL,
    registration_number VARCHAR(120),
    country_code VARCHAR(2),
    province_state VARCHAR(120),
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_platform_billing_tax_rules_code UNIQUE (code),
    CONSTRAINT ck_platform_billing_tax_rule_rate CHECK (rate BETWEEN 0 AND 1)
);

ALTER TABLE merchant_billing_contacts
    ADD CONSTRAINT fk_merchant_billing_contact_tax_rule FOREIGN KEY (tax_rule_id) REFERENCES platform_billing_tax_rules(id);

CREATE TABLE platform_billing_settings (
    id UUID PRIMARY KEY,
    legal_name VARCHAR(255),
    billing_address VARCHAR(2000),
    support_email VARCHAR(320),
    invoice_sender_email VARCHAR(320),
    default_currency VARCHAR(3) NOT NULL,
    default_payment_terms_days INTEGER NOT NULL,
    invoice_prefix VARCHAR(20) NOT NULL,
    tax_registration_number VARCHAR(120),
    default_tax_rule_id UUID,
    invoice_footer VARCHAR(2000),
    payment_instructions VARCHAR(4000),
    billing_enforcement_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_platform_billing_settings_terms CHECK (default_payment_terms_days >= 0),
    CONSTRAINT fk_platform_billing_settings_tax_rule FOREIGN KEY (default_tax_rule_id) REFERENCES platform_billing_tax_rules(id)
);

INSERT INTO platform_billing_settings (id, default_currency, default_payment_terms_days, invoice_prefix)
VALUES ('00000000-0000-0000-0000-000000000b75', 'CAD', 30, 'MTL')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE platform_invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(80) NOT NULL,
    tenant_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    pricing_plan_id UUID,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    subtotal NUMERIC(19, 4) NOT NULL,
    discount_total NUMERIC(19, 4) NOT NULL,
    tax_total NUMERIC(19, 4) NOT NULL,
    total NUMERIC(19, 4) NOT NULL,
    amount_paid NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount_outstanding NUMERIC(19, 4) NOT NULL,
    status VARCHAR(30) NOT NULL,
    merchant_business_name_snapshot VARCHAR(255) NOT NULL,
    merchant_billing_email_snapshot VARCHAR(320) NOT NULL,
    merchant_billing_address_snapshot VARCHAR(2000),
    tax_label_snapshot VARCHAR(120),
    tax_rate_snapshot NUMERIC(9, 6),
    tax_registration_number_snapshot VARCHAR(120),
    notes VARCHAR(2000),
    issued_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    voided_at TIMESTAMPTZ,
    external_invoice_id VARCHAR(180),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_platform_invoices_number UNIQUE (invoice_number),
    CONSTRAINT uq_platform_invoices_period UNIQUE (subscription_id, billing_period_start, billing_period_end),
    CONSTRAINT fk_platform_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_platform_invoices_subscription FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions(id),
    CONSTRAINT fk_platform_invoices_plan FOREIGN KEY (pricing_plan_id) REFERENCES platform_pricing_plans(id),
    CONSTRAINT ck_platform_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'SENT', 'PARTIALLY_PAID', 'PAID', 'PAST_DUE', 'VOID', 'CANCELLED')),
    CONSTRAINT ck_platform_invoice_dates CHECK (billing_period_end >= billing_period_start AND due_date >= issue_date),
    CONSTRAINT ck_platform_invoice_amounts CHECK (subtotal >= 0 AND discount_total >= 0 AND tax_total >= 0 AND total >= 0 AND amount_paid >= 0 AND amount_outstanding >= 0)
);

CREATE TABLE platform_invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    line_type VARCHAR(40) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    discount_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    line_subtotal NUMERIC(19, 4) NOT NULL,
    line_total NUMERIC(19, 4) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_platform_invoice_lines_invoice FOREIGN KEY (invoice_id) REFERENCES platform_invoices(id) ON DELETE CASCADE,
    CONSTRAINT ck_platform_invoice_line_type CHECK (line_type IN ('BASE_SUBSCRIPTION', 'ADDITIONAL_STORE', 'ADDITIONAL_REGISTER', 'ADDITIONAL_USER', 'DISCOUNT', 'ADJUSTMENT', 'OTHER')),
    CONSTRAINT ck_platform_invoice_line_amounts CHECK (quantity >= 0 AND unit_price >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND line_subtotal >= 0 AND line_total >= 0)
);

CREATE TABLE platform_invoice_payments (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    payment_date DATE NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    reference VARCHAR(180),
    notes VARCHAR(1000),
    external_payment_provider VARCHAR(80),
    external_payment_id VARCHAR(180),
    recorded_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_platform_invoice_payments_invoice FOREIGN KEY (invoice_id) REFERENCES platform_invoices(id),
    CONSTRAINT ck_platform_invoice_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_platform_invoice_payment_method CHECK (payment_method IN ('E_TRANSFER', 'BANK_TRANSFER', 'CHEQUE', 'CASH', 'OTHER'))
);

CREATE TABLE platform_invoice_email_deliveries (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    email_delivery_id UUID,
    status VARCHAR(40) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ,
    failure_code VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_platform_invoice_email_invoice FOREIGN KEY (invoice_id) REFERENCES platform_invoices(id),
    CONSTRAINT fk_platform_invoice_email_delivery FOREIGN KEY (email_delivery_id) REFERENCES email_deliveries(id) ON DELETE SET NULL
);

CREATE INDEX idx_platform_pricing_plans_status ON platform_pricing_plans(status);
CREATE INDEX idx_platform_invoices_tenant_date ON platform_invoices(tenant_id, issue_date DESC);
CREATE INDEX idx_platform_invoices_subscription ON platform_invoices(subscription_id, issue_date DESC);
CREATE INDEX idx_platform_invoices_status_due ON platform_invoices(status, due_date);
CREATE INDEX idx_platform_invoice_payments_invoice ON platform_invoice_payments(invoice_id, payment_date);

INSERT INTO security_permissions (id, code, description)
SELECT md5('permission:' || code)::UUID, code, description
FROM (VALUES
    ('PLATFORM_BILLING_VIEW', 'View platform billing dashboards.'),
    ('PLATFORM_PRICING_VIEW', 'View platform pricing plans.'),
    ('PLATFORM_PRICING_CREATE', 'Create platform pricing plans.'),
    ('PLATFORM_PRICING_UPDATE', 'Update platform pricing plans.'),
    ('PLATFORM_SUBSCRIPTION_VIEW', 'View merchant billing subscriptions.'),
    ('PLATFORM_SUBSCRIPTION_CREATE', 'Assign merchant billing subscriptions.'),
    ('PLATFORM_SUBSCRIPTION_UPDATE', 'Update merchant billing subscriptions.'),
    ('PLATFORM_SUBSCRIPTION_CANCEL', 'Cancel merchant billing subscriptions.'),
    ('PLATFORM_INVOICE_VIEW', 'View platform invoices.'),
    ('PLATFORM_INVOICE_CREATE', 'Generate platform invoices.'),
    ('PLATFORM_INVOICE_SEND', 'Send platform invoices.'),
    ('PLATFORM_INVOICE_VOID', 'Void platform invoices.'),
    ('PLATFORM_PAYMENT_RECORD', 'Record platform invoice payments.'),
    ('PLATFORM_BILLING_SETTINGS_MANAGE', 'Manage platform billing settings.'),
    ('MERCHANT_BILLING_VIEW', 'View the current tenant subscription and invoices.')
) permissions(code, description)
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code LIKE 'PLATFORM\_%' ESCAPE '\'
WHERE role.name = 'PLATFORM_SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'PLATFORM_BILLING_VIEW', 'PLATFORM_PRICING_VIEW', 'PLATFORM_SUBSCRIPTION_VIEW', 'PLATFORM_INVOICE_VIEW'
)
WHERE role.name = 'PLATFORM_SUPPORT_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'MERCHANT_BILLING_VIEW'
WHERE role.name IN ('TENANT_OWNER', 'OWNER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
