ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS country_id UUID,
    ADD COLUMN IF NOT EXISTS administrative_division_id UUID,
    ADD COLUMN IF NOT EXISTS default_currency_id UUID,
    ADD COLUMN IF NOT EXISTS primary_timezone_id UUID,
    ADD COLUMN IF NOT EXISTS default_tax_region_id UUID,
    ADD COLUMN IF NOT EXISTS administrative_division_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS default_tax_region_code VARCHAR(64);

ALTER TABLE merchant_profiles
    ADD COLUMN IF NOT EXISTS country_id UUID,
    ADD COLUMN IF NOT EXISTS administrative_division_id UUID,
    ADD COLUMN IF NOT EXISTS default_currency_id UUID,
    ADD COLUMN IF NOT EXISTS primary_timezone_id UUID,
    ADD COLUMN IF NOT EXISTS default_tax_region_id UUID,
    ADD COLUMN IF NOT EXISTS default_currency_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS primary_timezone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS default_tax_region_code VARCHAR(64);

UPDATE tenants tenant
SET country_id = country.id
FROM countries country
WHERE tenant.country_id IS NULL
  AND upper(country.code) = upper(tenant.country_code);

UPDATE tenants tenant
SET default_currency_id = currency.id
FROM currencies currency
WHERE tenant.default_currency_id IS NULL
  AND upper(currency.code) = upper(tenant.default_currency_code);

UPDATE tenants tenant
SET primary_timezone_id = timezone.id
FROM timezone_reference timezone
WHERE tenant.primary_timezone_id IS NULL
  AND lower(timezone.iana_name) = lower(tenant.primary_timezone);

UPDATE tenants tenant
SET administrative_division_code = profile.administrative_division_code
FROM merchant_profiles profile
WHERE profile.tenant_id = tenant.id
  AND tenant.administrative_division_code IS NULL
  AND profile.administrative_division_code IS NOT NULL;

UPDATE tenants tenant
SET administrative_division_id = area.id
FROM administrative_areas area
WHERE tenant.administrative_division_id IS NULL
  AND tenant.country_id = area.country_id
  AND upper(area.code) = upper(tenant.administrative_division_code);

UPDATE merchant_profiles profile
SET country_id = country.id
FROM countries country
WHERE profile.country_id IS NULL
  AND upper(country.code) = upper(profile.country_code);

UPDATE merchant_profiles profile
SET administrative_division_id = area.id
FROM administrative_areas area
WHERE profile.administrative_division_id IS NULL
  AND profile.country_id = area.country_id
  AND upper(area.code) = upper(profile.administrative_division_code);

UPDATE merchant_profiles profile
SET default_currency_code = tenant.default_currency_code,
    primary_timezone = tenant.primary_timezone,
    default_tax_region_code = tenant.default_tax_region_code,
    default_currency_id = tenant.default_currency_id,
    primary_timezone_id = tenant.primary_timezone_id,
    default_tax_region_id = tenant.default_tax_region_id
FROM tenants tenant
WHERE tenant.id = profile.tenant_id;

ALTER TABLE tenants
    ADD CONSTRAINT fk_tenants_country_id FOREIGN KEY (country_id) REFERENCES countries(id),
    ADD CONSTRAINT fk_tenants_administrative_division_id FOREIGN KEY (administrative_division_id) REFERENCES administrative_areas(id),
    ADD CONSTRAINT fk_tenants_default_currency_id FOREIGN KEY (default_currency_id) REFERENCES currencies(id),
    ADD CONSTRAINT fk_tenants_primary_timezone_id FOREIGN KEY (primary_timezone_id) REFERENCES timezone_reference(id),
    ADD CONSTRAINT fk_tenants_default_tax_region_id FOREIGN KEY (default_tax_region_id) REFERENCES tax_regions(id);

ALTER TABLE merchant_profiles
    ADD CONSTRAINT fk_merchant_profiles_country_id FOREIGN KEY (country_id) REFERENCES countries(id),
    ADD CONSTRAINT fk_merchant_profiles_administrative_division_id FOREIGN KEY (administrative_division_id) REFERENCES administrative_areas(id),
    ADD CONSTRAINT fk_merchant_profiles_default_currency_id FOREIGN KEY (default_currency_id) REFERENCES currencies(id),
    ADD CONSTRAINT fk_merchant_profiles_primary_timezone_id FOREIGN KEY (primary_timezone_id) REFERENCES timezone_reference(id),
    ADD CONSTRAINT fk_merchant_profiles_default_tax_region_id FOREIGN KEY (default_tax_region_id) REFERENCES tax_regions(id);

CREATE INDEX IF NOT EXISTS idx_tenants_country_id ON tenants(country_id);
CREATE INDEX IF NOT EXISTS idx_tenants_administrative_division_id ON tenants(administrative_division_id);
CREATE INDEX IF NOT EXISTS idx_tenants_default_currency_id ON tenants(default_currency_id);
CREATE INDEX IF NOT EXISTS idx_tenants_primary_timezone_id ON tenants(primary_timezone_id);
CREATE INDEX IF NOT EXISTS idx_tenants_default_tax_region_id ON tenants(default_tax_region_id);
CREATE INDEX IF NOT EXISTS idx_merchant_profiles_country_id ON merchant_profiles(country_id);
CREATE INDEX IF NOT EXISTS idx_merchant_profiles_administrative_division_id ON merchant_profiles(administrative_division_id);
CREATE INDEX IF NOT EXISTS idx_merchant_profiles_default_currency_id ON merchant_profiles(default_currency_id);
CREATE INDEX IF NOT EXISTS idx_merchant_profiles_primary_timezone_id ON merchant_profiles(primary_timezone_id);
CREATE INDEX IF NOT EXISTS idx_merchant_profiles_default_tax_region_id ON merchant_profiles(default_tax_region_id);

INSERT INTO security_permissions (id, code, description)
VALUES
    (md5('permission:REFERENCE_DATA_VIEW')::UUID, 'REFERENCE_DATA_VIEW', 'View platform and merchant reference data.'),
    (md5('permission:TENANT_GEOGRAPHY_UPDATE')::UUID, 'TENANT_GEOGRAPHY_UPDATE', 'Update merchant tenant geography defaults.'),
    (md5('permission:TENANT_CURRENCY_OVERRIDE')::UUID, 'TENANT_CURRENCY_OVERRIDE', 'Override default country currency for merchant tenant defaults.'),
    (md5('permission:TENANT_TAX_REGION_UPDATE')::UUID, 'TENANT_TAX_REGION_UPDATE', 'Update merchant tenant tax-region defaults.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'REFERENCE_DATA_VIEW',
    'TENANT_GEOGRAPHY_UPDATE',
    'TENANT_CURRENCY_OVERRIDE',
    'TENANT_TAX_REGION_UPDATE'
)
WHERE role.name = 'PLATFORM_SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'REFERENCE_DATA_VIEW'
WHERE role.name = 'PLATFORM_SUPPORT_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DROP VIEW platform_tenant_summary;

CREATE VIEW platform_tenant_summary AS
SELECT
    tenant.id,
    tenant.tenant_code,
    tenant.legal_name,
    tenant.display_name,
    tenant.status,
    tenant.country_code,
    tenant.administrative_division_code,
    tenant.default_currency_code,
    tenant.primary_timezone,
    tenant.default_tax_region_code,
    owner.email AS primary_owner_email,
    subscription.plan_code AS subscription_plan,
    onboarding.current_stage AS onboarding_stage,
    COALESCE(store_counts.store_count, 0) AS store_count,
    COALESCE(user_counts.user_count, 0) AS user_count,
    tenant.created_at,
    tenant.activated_at,
    tenant.suspended_at,
    tenant.version
FROM tenants tenant
LEFT JOIN LATERAL (
    SELECT security_users.email
    FROM security_users
    JOIN security_user_roles user_role ON user_role.user_id = security_users.id
    JOIN security_roles role ON role.id = user_role.role_id
    WHERE security_users.tenant_id = tenant.id
      AND role.name IN ('TENANT_OWNER', 'OWNER')
    ORDER BY security_users.created_at ASC
    LIMIT 1
) owner ON TRUE
LEFT JOIN tenant_subscriptions subscription ON subscription.tenant_id = tenant.id
LEFT JOIN tenant_onboardings onboarding ON onboarding.tenant_id = tenant.id
LEFT JOIN LATERAL (
    SELECT count(*) AS store_count
    FROM stores
    WHERE stores.tenant_id = tenant.id
) store_counts ON TRUE
LEFT JOIN LATERAL (
    SELECT count(*) AS user_count
    FROM security_users
    WHERE security_users.tenant_id = tenant.id
) user_counts ON TRUE;
