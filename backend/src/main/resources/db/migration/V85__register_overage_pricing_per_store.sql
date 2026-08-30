ALTER TABLE platform_pricing_plan_versions
    ADD COLUMN included_registers_per_store INTEGER,
    ADD COLUMN additional_register_price NUMERIC(19,4),
    ADD COLUMN included_users INTEGER,
    ADD COLUMN additional_user_price NUMERIC(19,4),
    ADD CONSTRAINT ck_plan_version_register_pricing CHECK (
        (included_registers_per_store IS NULL OR included_registers_per_store >= 0)
        AND (additional_register_price IS NULL OR additional_register_price >= 0)
    );

UPDATE platform_pricing_plan_versions version
SET included_registers_per_store = COALESCE(NULLIF(version.snapshot->>'includedRegisters','')::INTEGER, plan.included_registers),
    additional_register_price = COALESCE(NULLIF(version.snapshot->>'additionalRegisterPrice','')::NUMERIC, plan.additional_register_price),
    included_users = COALESCE(NULLIF(version.snapshot->>'includedUsers','')::INTEGER, plan.included_users),
    additional_user_price = COALESCE(NULLIF(version.snapshot->>'additionalUserPrice','')::NUMERIC, plan.additional_user_price)
FROM platform_pricing_plans plan
WHERE plan.id = version.pricing_plan_id;

ALTER TABLE tenant_subscriptions
    ADD COLUMN included_registers_per_store_snapshot INTEGER,
    ADD COLUMN additional_register_price_snapshot NUMERIC(19,4),
    ADD COLUMN included_users_snapshot INTEGER,
    ADD COLUMN additional_user_price_snapshot NUMERIC(19,4),
    ADD CONSTRAINT ck_subscription_register_pricing_snapshot CHECK (
        (included_registers_per_store_snapshot IS NULL OR included_registers_per_store_snapshot >= 0)
        AND (additional_register_price_snapshot IS NULL OR additional_register_price_snapshot >= 0)
    );

UPDATE tenant_subscriptions subscription
SET included_registers_per_store_snapshot = plan.included_registers,
    additional_register_price_snapshot = 0,
    included_users_snapshot = plan.included_users,
    additional_user_price_snapshot = 0
FROM platform_pricing_plans plan
WHERE plan.id = subscription.pricing_plan_id;

CREATE INDEX idx_registers_active_store ON registers(store_id) WHERE active = TRUE;
