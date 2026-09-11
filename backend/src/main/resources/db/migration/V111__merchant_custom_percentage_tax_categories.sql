ALTER TABLE tax_categories DROP CONSTRAINT IF EXISTS uq_tax_categories_code;

ALTER TABLE tax_categories
    ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE RESTRICT,
    ADD COLUMN category_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN percentage_rate NUMERIC(9,4),
    ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE tax_categories ADD CONSTRAINT ck_tax_categories_custom_percentage
    CHECK ((category_type = 'STANDARD' AND percentage_rate IS NULL)
        OR (category_type = 'CUSTOM_PERCENTAGE' AND tenant_id IS NOT NULL
            AND percentage_rate IS NOT NULL AND percentage_rate >= 0 AND percentage_rate <= 100));

CREATE UNIQUE INDEX uq_tax_categories_system_code
    ON tax_categories (lower(code)) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX uq_tax_categories_tenant_code
    ON tax_categories (tenant_id, lower(code)) WHERE tenant_id IS NOT NULL;
CREATE INDEX ix_tax_categories_tenant_type_active
    ON tax_categories (tenant_id, category_type, active);

ALTER TABLE sale_items
    ADD COLUMN tax_category_code_snapshot VARCHAR(64),
    ADD COLUMN tax_category_name_snapshot VARCHAR(180),
    ADD COLUMN tax_category_type_snapshot VARCHAR(32),
    ADD COLUMN tax_rate_snapshot NUMERIC(9,4),
    ADD COLUMN taxable_amount_snapshot NUMERIC(12,2);
