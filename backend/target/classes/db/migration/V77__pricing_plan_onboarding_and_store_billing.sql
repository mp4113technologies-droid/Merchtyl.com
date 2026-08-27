ALTER TABLE platform_pricing_plans
    ADD COLUMN one_time_onboarding_fee NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_platform_pricing_plan_onboarding_fee CHECK (one_time_onboarding_fee >= 0);

ALTER TABLE tenant_subscriptions
    ADD COLUMN plan_name_snapshot VARCHAR(180),
    ADD COLUMN plan_code_snapshot VARCHAR(80),
    ADD COLUMN included_stores_snapshot INTEGER,
    ADD COLUMN additional_store_price_snapshot NUMERIC(19, 4),
    ADD COLUMN onboarding_fee_snapshot NUMERIC(19, 4),
    ADD COLUMN custom_onboarding_fee NUMERIC(19, 4),
    ADD COLUMN onboarding_fee_invoiced_at TIMESTAMPTZ,
    ADD COLUMN onboarding_fee_invoice_id UUID,
    ADD CONSTRAINT ck_tenant_subscription_store_snapshot CHECK (included_stores_snapshot IS NULL OR included_stores_snapshot >= 0),
    ADD CONSTRAINT ck_tenant_subscription_store_price_snapshot CHECK (additional_store_price_snapshot IS NULL OR additional_store_price_snapshot >= 0),
    ADD CONSTRAINT ck_tenant_subscription_onboarding_snapshot CHECK (onboarding_fee_snapshot IS NULL OR onboarding_fee_snapshot >= 0),
    ADD CONSTRAINT ck_tenant_subscription_custom_onboarding CHECK (custom_onboarding_fee IS NULL OR custom_onboarding_fee >= 0);

UPDATE tenant_subscriptions subscription
SET plan_name_snapshot = plan.name,
    plan_code_snapshot = plan.code,
    included_stores_snapshot = plan.included_stores,
    additional_store_price_snapshot = plan.additional_store_price,
    onboarding_fee_snapshot = plan.one_time_onboarding_fee
FROM platform_pricing_plans plan
WHERE subscription.pricing_plan_id = plan.id;

UPDATE tenant_subscriptions
SET plan_code_snapshot = plan_code,
    plan_name_snapshot = plan_code,
    base_price_snapshot = COALESCE(base_price_snapshot, 0),
    included_stores_snapshot = COALESCE(included_stores_snapshot, maximum_stores, 0),
    additional_store_price_snapshot = COALESCE(additional_store_price_snapshot, 0),
    onboarding_fee_snapshot = COALESCE(onboarding_fee_snapshot, 0),
    currency_code = COALESCE(currency_code, 'CAD')
WHERE pricing_plan_id IS NULL;

ALTER TABLE platform_invoice_lines DROP CONSTRAINT ck_platform_invoice_line_type;
ALTER TABLE platform_invoice_lines ADD CONSTRAINT ck_platform_invoice_line_type
    CHECK (line_type IN ('BASE_SUBSCRIPTION', 'ONBOARDING_FEE', 'ADDITIONAL_STORE', 'ADDITIONAL_REGISTER', 'ADDITIONAL_USER', 'DISCOUNT', 'ADJUSTMENT', 'OTHER'));

ALTER TABLE tenant_subscriptions
    ADD CONSTRAINT uq_tenant_subscription_onboarding_invoice UNIQUE (onboarding_fee_invoice_id),
    ADD CONSTRAINT fk_tenant_subscription_onboarding_invoice FOREIGN KEY (onboarding_fee_invoice_id) REFERENCES platform_invoices(id);
