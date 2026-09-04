ALTER TABLE tenants ADD COLUMN merchant_slug VARCHAR(63);

DO $$
DECLARE
    tenant_row RECORD;
    base_slug TEXT;
    candidate TEXT;
    suffix INTEGER;
BEGIN
    FOR tenant_row IN SELECT id, display_name FROM tenants ORDER BY created_at, id LOOP
        base_slug := lower(trim(both '-' from regexp_replace(regexp_replace(trim(tenant_row.display_name), '[^A-Za-z0-9]+', '-', 'g'), '-+', '-', 'g')));
        IF base_slug = '' THEN
            base_slug := 'merchant';
        END IF;
        base_slug := left(base_slug, 63);
        IF base_slug IN ('www','api','platform','admin','app','portal','login','logout','signup','support','help','status','billing','docs','assets','static','mail','cdn') THEN
            base_slug := left(base_slug, 54) || '-merchant';
        END IF;
        candidate := base_slug;
        suffix := 2;
        WHILE EXISTS (SELECT 1 FROM tenants WHERE merchant_slug = candidate) LOOP
            candidate := left(base_slug, 63 - length(suffix::text) - 1) || '-' || suffix;
            suffix := suffix + 1;
        END LOOP;
        UPDATE tenants SET merchant_slug = candidate WHERE id = tenant_row.id;
    END LOOP;
END $$;

ALTER TABLE tenants ALTER COLUMN merchant_slug SET NOT NULL;
ALTER TABLE tenants ADD CONSTRAINT uq_tenants_merchant_slug UNIQUE (merchant_slug);
ALTER TABLE tenants ADD CONSTRAINT ck_tenants_merchant_slug_format
    CHECK (merchant_slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$');

DROP VIEW platform_tenant_summary;
CREATE VIEW platform_tenant_summary AS
SELECT t.*,
       owner.email AS primary_owner_email,
       subscription.plan_code AS subscription_plan,
       onboarding.current_stage AS onboarding_stage,
       (SELECT count(*) FROM stores s WHERE s.tenant_id = t.id) AS store_count,
       (SELECT count(*) FROM security_users u WHERE u.tenant_id = t.id) AS user_count
FROM tenants t
LEFT JOIN LATERAL (
    SELECT u.email FROM security_users u WHERE u.tenant_id = t.id ORDER BY u.created_at LIMIT 1
) owner ON true
LEFT JOIN tenant_subscriptions subscription ON subscription.tenant_id = t.id
LEFT JOIN tenant_onboardings onboarding ON onboarding.tenant_id = t.id;
