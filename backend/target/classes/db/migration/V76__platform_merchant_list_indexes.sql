CREATE INDEX IF NOT EXISTS idx_tenants_created_id_desc
    ON tenants (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_tenants_status_created_desc
    ON tenants (status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_tenants_geography
    ON tenants (country_code, administrative_division_code);
