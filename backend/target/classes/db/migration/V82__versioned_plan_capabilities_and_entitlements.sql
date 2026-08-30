ALTER TABLE platform_pricing_plan_versions
    ADD COLUMN currency_code VARCHAR(3),
    ADD COLUMN billing_interval VARCHAR(20),
    ADD COLUMN base_price NUMERIC(19,4),
    ADD COLUMN included_stores INTEGER,
    ADD COLUMN additional_store_price NUMERIC(19,4),
    ADD COLUMN one_time_onboarding_fee NUMERIC(19,4),
    ADD COLUMN trial_days INTEGER,
    ADD COLUMN effective_to TIMESTAMPTZ,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN subscriber_policy VARCHAR(40) NOT NULL DEFAULT 'NEW_SUBSCRIPTIONS_ONLY',
    ADD COLUMN activated_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE platform_pricing_plan_capability_prices DROP CONSTRAINT ck_plan_capability_price_capability;
ALTER TABLE platform_pricing_plan_capability_prices ADD CONSTRAINT ck_plan_capability_price_capability CHECK(capability IN ('FOOD_SERVICE','LOTTERY','ADVANCED_REPORTING'));
ALTER TABLE tenant_subscription_capability_price_snapshots DROP CONSTRAINT ck_subscription_capability_snapshot_capability;
ALTER TABLE tenant_subscription_capability_price_snapshots ADD CONSTRAINT ck_subscription_capability_snapshot_capability CHECK(capability IN ('FOOD_SERVICE','LOTTERY','ADVANCED_REPORTING'));

UPDATE platform_pricing_plan_versions version
SET currency_code=COALESCE(version.snapshot->>'currency','CAD'),
    billing_interval=COALESCE(version.snapshot->>'billingInterval','MONTHLY'),
    base_price=COALESCE((version.snapshot->>'basePrice')::NUMERIC,plan.base_price),
    included_stores=COALESCE((version.snapshot->>'includedStores')::INTEGER,plan.included_stores),
    additional_store_price=COALESCE((version.snapshot->>'additionalStorePrice')::NUMERIC,plan.additional_store_price),
    one_time_onboarding_fee=COALESCE((version.snapshot->>'oneTimeOnboardingFee')::NUMERIC,plan.one_time_onboarding_fee),
    trial_days=COALESCE((version.snapshot->>'trialDays')::INTEGER,plan.trial_days),
    activated_at=CASE WHEN version.effective_from<=NOW() THEN version.effective_from END,
    status=CASE WHEN version.effective_from<=NOW() THEN 'ACTIVE' ELSE 'SCHEDULED' END
FROM platform_pricing_plans plan WHERE plan.id=version.pricing_plan_id;

ALTER TABLE platform_pricing_plan_versions ADD CONSTRAINT ck_pricing_version_status CHECK(status IN ('SCHEDULED','ACTIVE','SUPERSEDED','CANCELLED'));
CREATE INDEX idx_pricing_versions_effective ON platform_pricing_plan_versions(pricing_plan_id,effective_from,status);

CREATE TABLE platform_pricing_plan_version_capabilities (
    id UUID PRIMARY KEY,
    pricing_plan_version_id UUID NOT NULL REFERENCES platform_pricing_plan_versions(id) ON DELETE CASCADE,
    capability VARCHAR(60) NOT NULL,
    inclusion_type VARCHAR(30) NOT NULL,
    billing_unit VARCHAR(30),
    unit_price NUMERIC(19,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pricing_version_capability UNIQUE(pricing_plan_version_id,capability),
    CONSTRAINT ck_pricing_capability_name CHECK(capability IN ('RETAIL_POS','INVENTORY','REGISTER_MANAGEMENT','RETURNS','REPORTING','ADVANCED_REPORTING','EMPLOYEE_MANAGEMENT','FOOD_SERVICE','LOTTERY')),
    CONSTRAINT ck_pricing_capability_inclusion CHECK(inclusion_type IN ('INCLUDED','PAID_ADD_ON','NOT_AVAILABLE')),
    CONSTRAINT ck_pricing_capability_unit CHECK(billing_unit IS NULL OR billing_unit IN ('PER_MERCHANT','PER_STORE','PER_REGISTER')),
    CONSTRAINT ck_pricing_capability_price CHECK(unit_price IS NULL OR unit_price>=0),
    CONSTRAINT ck_pricing_capability_paid CHECK(inclusion_type<>'PAID_ADD_ON' OR (billing_unit IS NOT NULL AND unit_price IS NOT NULL))
);

INSERT INTO platform_pricing_plan_version_capabilities(id,pricing_plan_version_id,capability,inclusion_type,billing_unit,unit_price)
SELECT md5('version-capability:'||version.id||':'||price.capability)::UUID,version.id,price.capability,'PAID_ADD_ON','PER_STORE',price.monthly_price_per_store
FROM platform_pricing_plan_versions version
JOIN (SELECT pricing_plan_id,max(version_number) version_number FROM platform_pricing_plan_versions GROUP BY pricing_plan_id) latest USING(pricing_plan_id,version_number)
JOIN platform_pricing_plan_capability_prices price ON price.pricing_plan_id=version.pricing_plan_id;

ALTER TABLE tenant_subscriptions ADD COLUMN pricing_plan_version_id UUID REFERENCES platform_pricing_plan_versions(id);
UPDATE tenant_subscriptions subscription SET pricing_plan_version_id=(SELECT id FROM platform_pricing_plan_versions version WHERE version.pricing_plan_id=subscription.pricing_plan_id AND version.effective_from<=COALESCE(subscription.pricing_effective_from,CURRENT_DATE) ORDER BY version.effective_from DESC,version.version_number DESC LIMIT 1);

CREATE TABLE tenant_subscription_capabilities (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES tenant_subscriptions(id) ON DELETE CASCADE,
    capability VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL,
    inclusion_type_snapshot VARCHAR(30) NOT NULL,
    billing_unit_snapshot VARCHAR(30),
    unit_price_snapshot NUMERIC(19,4),
    custom_unit_price NUMERIC(19,4),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_subscription_capability UNIQUE(subscription_id,capability),
    CONSTRAINT ck_subscription_capability_status CHECK(status IN ('ACTIVE','PENDING','INACTIVE'))
);
CREATE INDEX idx_subscription_capability_status ON tenant_subscription_capabilities(subscription_id,status,capability);

INSERT INTO tenant_subscription_capabilities(id,subscription_id,capability,status,inclusion_type_snapshot,billing_unit_snapshot,unit_price_snapshot,effective_from)
SELECT md5('subscription-capability:'||subscription.id||':'||snapshot.capability)::UUID,subscription.id,snapshot.capability,'ACTIVE','PAID_ADD_ON','PER_STORE',snapshot.monthly_price_per_store,COALESCE(subscription.pricing_effective_from,CURRENT_DATE)
FROM tenant_subscriptions subscription JOIN tenant_subscription_capability_price_snapshots snapshot ON snapshot.subscription_id=subscription.id
WHERE EXISTS(SELECT 1 FROM stores store JOIN store_capabilities capability ON capability.store_id=store.id WHERE store.tenant_id=subscription.tenant_id AND store.active=true AND capability.capability=snapshot.capability);

INSERT INTO security_permissions(id,code,description) VALUES
 (md5('permission:PLATFORM_PRICING_PLAN_VIEW')::UUID,'PLATFORM_PRICING_PLAN_VIEW','View pricing plan versions.'),
 (md5('permission:PLATFORM_PRICING_PLAN_CREATE')::UUID,'PLATFORM_PRICING_PLAN_CREATE','Create pricing plans.'),
 (md5('permission:PLATFORM_PRICING_PLAN_EDIT')::UUID,'PLATFORM_PRICING_PLAN_EDIT','Edit pricing plan identity.'),
 (md5('permission:PLATFORM_PRICING_PLAN_PRICE_EDIT')::UUID,'PLATFORM_PRICING_PLAN_PRICE_EDIT','Edit plan component pricing.'),
 (md5('permission:PLATFORM_PRICING_PLAN_CAPABILITY_EDIT')::UUID,'PLATFORM_PRICING_PLAN_CAPABILITY_EDIT','Edit plan capabilities.'),
 (md5('permission:PLATFORM_PRICING_PLAN_VERSION_VIEW')::UUID,'PLATFORM_PRICING_PLAN_VERSION_VIEW','View pricing history.'),
 (md5('permission:PLATFORM_PRICING_PLAN_VERSION_SCHEDULE')::UUID,'PLATFORM_PRICING_PLAN_VERSION_SCHEDULE','Schedule pricing versions.'),
 (md5('permission:PLATFORM_PRICING_PLAN_VERSION_CANCEL')::UUID,'PLATFORM_PRICING_PLAN_VERSION_CANCEL','Cancel scheduled pricing versions.')
ON CONFLICT(code) DO NOTHING;
INSERT INTO security_role_permissions(id,role_id,permission_id)
SELECT md5('role-permission:'||role.name||':'||permission.code)::UUID,role.id,permission.id FROM security_roles role CROSS JOIN security_permissions permission
WHERE role.name='PLATFORM_SUPER_ADMIN' AND permission.code LIKE 'PLATFORM_PRICING_PLAN_%' ON CONFLICT(role_id,permission_id) DO NOTHING;
