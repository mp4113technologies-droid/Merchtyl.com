CREATE TABLE platform_pricing_plan_capability_prices (
    pricing_plan_id UUID NOT NULL REFERENCES platform_pricing_plans(id) ON DELETE CASCADE,
    capability VARCHAR(40) NOT NULL,
    monthly_price_per_store NUMERIC(19,4) NOT NULL,
    PRIMARY KEY (pricing_plan_id, capability),
    CONSTRAINT ck_plan_capability_price_capability CHECK (capability IN ('FOOD_SERVICE')),
    CONSTRAINT ck_plan_capability_price_amount CHECK (monthly_price_per_store >= 0)
);

CREATE TABLE tenant_subscription_capability_price_snapshots (
    subscription_id UUID NOT NULL REFERENCES tenant_subscriptions(id) ON DELETE CASCADE,
    capability VARCHAR(40) NOT NULL,
    monthly_price_per_store NUMERIC(19,4) NOT NULL,
    PRIMARY KEY (subscription_id, capability),
    CONSTRAINT ck_subscription_capability_snapshot_capability CHECK (capability IN ('FOOD_SERVICE')),
    CONSTRAINT ck_subscription_capability_snapshot_amount CHECK (monthly_price_per_store >= 0)
);

ALTER TABLE platform_invoice_lines DROP CONSTRAINT ck_platform_invoice_line_type;
ALTER TABLE platform_invoice_lines ADD CONSTRAINT ck_platform_invoice_line_type CHECK (line_type IN (
    'BASE_SUBSCRIPTION', 'ONBOARDING_FEE', 'ADDITIONAL_STORE', 'CAPABILITY_ADD_ON',
    'ADDITIONAL_REGISTER', 'ADDITIONAL_USER', 'DISCOUNT', 'ADJUSTMENT', 'OTHER'
));
